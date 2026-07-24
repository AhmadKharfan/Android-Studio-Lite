package com.ahmadkharfan.androidstudiolite.data.ai

import com.ahmadkharfan.androidstudiolite.data.ai.agent.AgentProtocol
import com.ahmadkharfan.androidstudiolite.data.ai.agent.AgentToolExecutor
import com.ahmadkharfan.androidstudiolite.data.ai.agent.AiAgentLog
import com.ahmadkharfan.androidstudiolite.domain.model.AiAgentSettings
import com.ahmadkharfan.androidstudiolite.domain.model.AiProviderConfig
import com.ahmadkharfan.androidstudiolite.domain.model.ApiKeyStatus
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMessage
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMode
import com.ahmadkharfan.androidstudiolite.domain.model.ChatThreadSelection
import com.ahmadkharfan.androidstudiolite.domain.model.ChatThreadSummary
import com.ahmadkharfan.androidstudiolite.domain.repository.AiAgentRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.AiChatRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

class RealAiChatRepository(
    private val aiAgentRepository: AiAgentRepository,
    private val keyStore: EncryptedAiKeyStore,
    private val gateway: AiLlmGateway,
    private val executor: AgentToolExecutor,
    private val historyStore: ChatHistoryStore,
) : AiChatRepository {

    private val messageEditor = ChatMessageEditor()
    private val sessionStore = ChatSessionStore(historyStore) { messageEditor.newThread() }
    private val pendingApprovals = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val agentRunner = AgentTurnRunner(
        streamer = LlmStreamer { request, onDelta ->
            gateway.chatRawStream(
                request.providerId,
                request.apiKey,
                request.model,
                request.systemPrompt,
                request.turns,
                request.baseUrl,
                onDelta,
            )
        },
        tools = executor,
        messages = messageEditor,
        pendingApprovals = pendingApprovals,
    )

    override fun observeMessages(projectId: String): Flow<List<ChatMessage>> =
        flow { emitAll(sessionStore.sessionFor(projectId).messages()) }

    override fun observeThreads(projectId: String): Flow<List<ChatThreadSummary>> =
        flow { emitAll(sessionStore.sessionFor(projectId).summaries()) }

    override fun observeActiveThreadId(projectId: String): Flow<String> =
        flow { emitAll(sessionStore.sessionFor(projectId).activeThreadId) }

    override fun observeActiveSelection(projectId: String): Flow<ChatThreadSelection> =
        flow { emitAll(sessionStore.sessionFor(projectId).selection()) }

    override suspend fun newChat(projectId: String) = sessionStore.newChat(projectId)

    override suspend fun selectThread(projectId: String, threadId: String) =
        sessionStore.selectThread(projectId, threadId)

    override suspend fun deleteThread(projectId: String, threadId: String) =
        sessionStore.deleteThread(projectId, threadId)

    override suspend fun setThreadMode(projectId: String, threadId: String, mode: ChatMode) =
        sessionStore.setThreadMode(projectId, threadId, mode)

    override suspend fun setThreadModelSelection(projectId: String, threadId: String, providerId: String, model: String) {
        sessionStore.setThreadModelSelection(projectId, threadId, providerId, model)
        aiAgentRepository.setActiveProvider(providerId)
        if (model.isNotBlank()) aiAgentRepository.setModel(providerId, model)
    }

    override suspend fun buildFromPlan(projectId: String, planMessageId: String, activeFilePath: String?) {
        val session = sessionStore.sessionFor(projectId)
        val threadId = session.activeThreadId.value
        setThreadMode(projectId, threadId, ChatMode.AGENT)
        val planText = session.activeMessages()
            .firstOrNull { it.id == planMessageId }
            ?.text
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: findPlanMessage(session.activeMessages())?.text?.trim()?.takeIf { it.isNotBlank() }
        val message = if (planText != null) {
            AgentProtocol.implementPlanPrompt(planText)
        } else {
            AgentProtocol.BUILD_PLAN_USER_MESSAGE
        }
        AiAgentLog.i(
            "Build",
            "buildFromPlan planMessageId=$planMessageId foundPlan=${planText != null} " +
                "planLen=${planText?.length ?: 0} messageLen=${message.length}",
        )
        sendMessage(message, projectId, activeFilePath)
    }

    override suspend fun reviewPlan(
        projectId: String,
        planMessageId: String,
        activeFilePath: String?,
        userInstructions: String?,
    ) {
        val session = sessionStore.sessionFor(projectId)
        val threadId = session.activeThreadId.value
        setThreadMode(projectId, threadId, ChatMode.ASK)
        sendMessage(AgentProtocol.reviewPlanPrompt(userInstructions), projectId, activeFilePath)
    }

    override suspend fun sendMessage(text: String, projectId: String, activeFilePath: String?) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val session = sessionStore.sessionFor(projectId)
        val settings = aiAgentRepository.observeSettings().first()
        val thread = session.activeThread()
        val mode = thread?.mode ?: ChatMode.AGENT
        messageEditor.appendUserMessage(session, trimmed)
        session.setTitleIfBlank(trimmed.take(60).replace('\n', ' '))
        sessionStore.persist(session)

        try {
            val provider = resolveChatProvider(settings, thread)
            if (provider == null) {
                messageEditor.appendAiMessage(session, "No validated AI provider. Add an API key in Settings and tap Test.")
                return
            }
            val apiKey = keyStore.getKey(provider.id)
            if (apiKey.isBlank()) {
                messageEditor.appendAiMessage(session, "API key missing for ${provider.name}. Re-enter it in Settings.")
                return
            }
            val model = thread?.model?.takeIf { it.isNotBlank() } ?: provider.selectedModel
            agentRunner.run(AgentRequest(session, provider, apiKey, model, mode, settings, activeFilePath, trimmed))
        } finally {
            sessionStore.persist(session)
        }
    }


    override suspend fun markApplied(projectId: String, messageId: String) {
        val session = sessionStore.sessionFor(projectId)
        session.updateActiveMessages { list -> list.map { if (it.id == messageId) it.copy(applied = true) else it } }
        sessionStore.persist(session)
    }

    override fun approveTool(toolCallId: String) {
        pendingApprovals[toolCallId]?.complete(true)
    }

    override fun rejectTool(toolCallId: String) {
        pendingApprovals[toolCallId]?.complete(false)
    }
}

internal fun AiAgentSettings.activeProvider(): AiProviderConfig? {
    if (!enabled) return null
    val byId = providers.associateBy { it.id }
    val valid = AiProviderCatalog.all.mapNotNull { byId[it.id] }.filter { it.status == ApiKeyStatus.VALID }
    return valid.firstOrNull { it.id == activeProviderId } ?: valid.firstOrNull()
}
