package com.ahmadkharfan.androidstudiolite.data.ai

import com.ahmadkharfan.androidstudiolite.data.ai.agent.AgentProtocol
import com.ahmadkharfan.androidstudiolite.data.ai.agent.AgentToolExecutor
import com.ahmadkharfan.androidstudiolite.data.ai.agent.AgentTurn
import com.ahmadkharfan.androidstudiolite.domain.model.AgentAction
import com.ahmadkharfan.androidstudiolite.domain.model.AgentToolResult
import com.ahmadkharfan.androidstudiolite.domain.model.AiAgentSettings
import com.ahmadkharfan.androidstudiolite.domain.model.AiProviderConfig
import com.ahmadkharfan.androidstudiolite.domain.model.ApiKeyStatus
import com.ahmadkharfan.androidstudiolite.domain.model.ChatCodeSnippet
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMessage
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMode
import com.ahmadkharfan.androidstudiolite.domain.model.ChatRole
import com.ahmadkharfan.androidstudiolite.domain.model.ChatThread
import com.ahmadkharfan.androidstudiolite.domain.model.ChatThreadSelection
import com.ahmadkharfan.androidstudiolite.domain.model.ChatThreadSummary
import com.ahmadkharfan.androidstudiolite.domain.model.ChatToolCall
import com.ahmadkharfan.androidstudiolite.domain.model.ToolCallStatus
import com.ahmadkharfan.androidstudiolite.domain.repository.AiAgentRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.AiChatRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RealAiChatRepository(
    private val aiAgentRepository: AiAgentRepository,
    private val keyStore: EncryptedAiKeyStore,
    private val gateway: AiLlmGateway,
    private val executor: AgentToolExecutor,
    private val historyStore: ChatHistoryStore,
) : AiChatRepository {

    /** In-memory, per-project conversation state backed by [historyStore] on disk. */
    private inner class ProjectSession(val projectId: String) {
        val threads = MutableStateFlow<List<ChatThread>>(emptyList())
        val activeThreadId = MutableStateFlow("")

        fun messages(): Flow<List<ChatMessage>> = combine(threads, activeThreadId) { list, id ->
            list.firstOrNull { it.id == id }?.messages ?: emptyList()
        }

        fun summaries(): Flow<List<ChatThreadSummary>> = threads.map { list ->
            list.sortedByDescending { it.updatedAt }
                .map { ChatThreadSummary(it.id, it.title.ifBlank { UNTITLED }, it.updatedAt, it.messages.count { m -> m.id != WELCOME_ID }) }
        }

        fun selection(): Flow<ChatThreadSelection> = combine(threads, activeThreadId) { list, id ->
            val thread = list.firstOrNull { it.id == id }
            ChatThreadSelection(id, thread?.mode ?: ChatMode.AGENT, thread?.providerId, thread?.model)
        }

        fun updateActiveMessages(transform: (List<ChatMessage>) -> List<ChatMessage>) {
            val id = activeThreadId.value
            threads.value = threads.value.map { thread ->
                if (thread.id == id) {
                    thread.copy(messages = transform(thread.messages), updatedAt = System.currentTimeMillis())
                } else {
                    thread
                }
            }
        }

        fun setTitleIfBlank(title: String) {
            val id = activeThreadId.value
            threads.value = threads.value.map { thread ->
                if (thread.id == id && thread.title.isBlank()) thread.copy(title = title) else thread
            }
        }

        fun updateThreadById(threadId: String, transform: (ChatThread) -> ChatThread) {
            threads.value = threads.value.map { thread -> if (thread.id == threadId) transform(thread) else thread }
        }
    }

    private val sessions = HashMap<String, ProjectSession>()
    private val sessionMutex = Mutex()
    private val pendingApprovals = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    private suspend fun sessionFor(projectId: String): ProjectSession = sessionMutex.withLock {
        sessions[projectId]?.let { return it }
        val saved = historyStore.load(projectId)
        val session = ProjectSession(projectId)
        if (saved.threads.isEmpty()) {
            val thread = newThread()
            session.threads.value = listOf(thread)
            session.activeThreadId.value = thread.id
        } else {
            session.threads.value = saved.threads
            session.activeThreadId.value = saved.activeThreadId.ifBlank { saved.threads.maxByOrNull { it.updatedAt }!!.id }
        }
        sessions[projectId] = session
        session
    }

    private suspend fun persist(session: ProjectSession) {
        historyStore.save(session.projectId, ProjectChats(session.activeThreadId.value, session.threads.value))
    }

    override fun observeMessages(projectId: String): Flow<List<ChatMessage>> =
        flow { emitAll(sessionFor(projectId).messages()) }

    override fun observeThreads(projectId: String): Flow<List<ChatThreadSummary>> =
        flow { emitAll(sessionFor(projectId).summaries()) }

    override fun observeActiveThreadId(projectId: String): Flow<String> =
        flow { emitAll(sessionFor(projectId).activeThreadId) }

    override fun observeActiveSelection(projectId: String): Flow<ChatThreadSelection> =
        flow { emitAll(sessionFor(projectId).selection()) }

    override suspend fun newChat(projectId: String) {
        val session = sessionFor(projectId)
        val thread = newThread()
        session.threads.value = session.threads.value + thread
        session.activeThreadId.value = thread.id
        persist(session)
    }

    override suspend fun selectThread(projectId: String, threadId: String) {
        val session = sessionFor(projectId)
        if (session.threads.value.any { it.id == threadId }) {
            session.activeThreadId.value = threadId
            persist(session)
        }
    }

    override suspend fun deleteThread(projectId: String, threadId: String) {
        val session = sessionFor(projectId)
        val remaining = session.threads.value.filterNot { it.id == threadId }
        if (remaining.isEmpty()) {
            val thread = newThread()
            session.threads.value = listOf(thread)
            session.activeThreadId.value = thread.id
        } else {
            session.threads.value = remaining
            if (session.activeThreadId.value == threadId) {
                session.activeThreadId.value = remaining.maxByOrNull { it.updatedAt }!!.id
            }
        }
        persist(session)
    }

    override suspend fun setThreadMode(projectId: String, threadId: String, mode: ChatMode) {
        val session = sessionFor(projectId)
        session.updateThreadById(threadId) { it.copy(mode = mode) }
        persist(session)
    }

    override suspend fun setThreadModelSelection(projectId: String, threadId: String, providerId: String, model: String) {
        val session = sessionFor(projectId)
        session.updateThreadById(threadId) { it.copy(providerId = providerId, model = model) }
        persist(session)
        // Remember this choice as the default for new chats.
        aiAgentRepository.setActiveProvider(providerId)
        if (model.isNotBlank()) aiAgentRepository.setModel(providerId, model)
    }

    override suspend fun sendMessage(text: String, projectId: String, activeFilePath: String?) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val session = sessionFor(projectId)
        val settings = aiAgentRepository.observeSettings().first()
        val thread = activeThread(session)
        val mode = thread?.mode ?: ChatMode.AGENT
        appendUserMessage(session, trimmed)
        session.setTitleIfBlank(trimmed.take(60).replace('\n', ' '))
        persist(session)

        try {
            val provider = resolveProvider(settings, thread)
            if (provider == null) {
                appendAiMessage(session, "No validated AI provider. Add an API key in Settings and tap Test.")
                return
            }
            val apiKey = keyStore.getKey(provider.id)
            if (apiKey.isBlank()) {
                appendAiMessage(session, "API key missing for ${provider.name}. Re-enter it in Settings.")
                return
            }
            val model = thread?.model?.takeIf { it.isNotBlank() } ?: provider.selectedModel
            runAgentLoop(session, provider, apiKey, model, mode, settings, activeFilePath)
        } finally {
            persist(session)
        }
    }

    /** Picks the thread's provider if it's validated, else the default/active provider, else the first valid one. */
    private fun resolveProvider(settings: AiAgentSettings, thread: ChatThread?): AiProviderConfig? {
        if (!settings.enabled) return null
        val valid = settings.providers.filter { it.status == ApiKeyStatus.VALID }
        thread?.providerId?.let { id -> valid.firstOrNull { it.id == id } }?.let { return it }
        return settings.activeProvider()
    }

    private suspend fun runAgentLoop(
        session: ProjectSession,
        provider: AiProviderConfig,
        apiKey: String,
        model: String,
        mode: ChatMode,
        settings: AiAgentSettings,
        activeFilePath: String?,
    ) {
        val projectId = session.projectId
        val outline = executor.outline(projectId)
        val system = AgentProtocol.systemPrompt(settings.instructions, outline, activeFilePath, mode)

        // Seed the model conversation with prior plain-text messages (skip the welcome + tool cards).
        // Consecutive same-role turns are merged so providers that require strict user/assistant
        // alternation (e.g. Anthropic) don't reject the request.
        val turns = coalesce(
            activeMessages(session)
                .filter { it.id != WELCOME_ID && it.toolCall == null && it.text.isNotBlank() }
                .map { LlmChatTurn(it.role, it.text) },
        ).toMutableList()

        var iteration = 0
        while (iteration < MAX_ITERATIONS) {
            iteration++
            val raw = runCatching { gateway.chatRaw(provider.id, apiKey, model, system, turns) }
                .getOrElse { error ->
                    val detail = (error as? AiLlmException)?.message ?: error.message ?: "Unknown error"
                    appendAiMessage(session, "Request failed: $detail")
                    return
                }
            turns.add(LlmChatTurn(ChatRole.AI, raw))

            when (val turn = AgentProtocol.parse(raw)) {
                is AgentTurn.Final -> {
                    appendAiMessage(session, turn.text)
                    return
                }

                is AgentTurn.Actions -> {
                    turn.thought?.takeIf { it.isNotBlank() }?.let { appendAiMessage(session, it) }
                    val results = turn.actions.map { runAction(session, it, settings.autoApply, mode) }
                    turns.add(LlmChatTurn(ChatRole.USER, formatResults(results)))
                }
            }
        }
        appendAiMessage(session, "Stopped after $MAX_ITERATIONS steps to avoid running away. Ask me to continue if needed.")
    }

    private suspend fun runAction(
        session: ProjectSession,
        action: AgentAction,
        autoApply: Boolean,
        mode: ChatMode,
    ): AgentToolResult {
        val projectId = session.projectId
        // In read-only modes (Ask/Plan) file-mutating tools are refused so the model must answer/plan instead.
        if (mode != ChatMode.AGENT && action.mutating) {
            val toolCallId = "tool-${UUID.randomUUID()}"
            appendToolMessage(
                session,
                ChatToolCall(
                    id = toolCallId,
                    tool = action.tool,
                    path = action.pathOrNull(),
                    summary = action.summary(),
                    status = ToolCallStatus.REJECTED,
                    resultText = "Blocked: ${mode.name.lowercase()} mode is read-only.",
                    mutating = true,
                ),
            )
            return AgentToolResult(action, ok = false, output = "Blocked: ${mode.name.lowercase()} mode is read-only; do not edit files.")
        }
        val toolCallId = "tool-${UUID.randomUUID()}"
        val (diffOld, diffNew) = buildDiff(projectId, action)
        val initialStatus = if (!action.mutating || autoApply) ToolCallStatus.RUNNING else ToolCallStatus.PENDING
        appendToolMessage(
            session,
            ChatToolCall(
                id = toolCallId,
                tool = action.tool,
                path = action.pathOrNull(),
                summary = action.summary(),
                diffOld = diffOld,
                diffNew = diffNew,
                status = initialStatus,
                mutating = action.mutating,
            ),
        )

        if (action.mutating && !autoApply) {
            val deferred = CompletableDeferred<Boolean>()
            pendingApprovals[toolCallId] = deferred
            val approved = deferred.await()
            pendingApprovals.remove(toolCallId)
            if (!approved) {
                updateToolCall(session, toolCallId, ToolCallStatus.REJECTED, "Rejected by user")
                return AgentToolResult(action, ok = false, output = "User rejected this change.")
            }
            updateToolCall(session, toolCallId, ToolCallStatus.RUNNING, null)
        }

        val result = executor.run(projectId, action)
        updateToolCall(
            session,
            toolCallId,
            if (result.ok) ToolCallStatus.DONE else ToolCallStatus.FAILED,
            result.output.take(MAX_CARD_OUTPUT),
        )
        return result
    }

    private suspend fun buildDiff(projectId: String, action: AgentAction): Pair<String?, String?> = when (action) {
        is AgentAction.CreateFile -> null to action.content
        is AgentAction.EditFile -> executor.readTextOrNull(projectId, action.path) to action.content
        else -> null to null
    }

    override suspend fun markApplied(projectId: String, messageId: String) {
        val session = sessionFor(projectId)
        session.updateActiveMessages { list -> list.map { if (it.id == messageId) it.copy(applied = true) else it } }
        persist(session)
    }

    override fun approveTool(toolCallId: String) {
        pendingApprovals[toolCallId]?.complete(true)
    }

    override fun rejectTool(toolCallId: String) {
        pendingApprovals[toolCallId]?.complete(false)
    }

    private fun activeThread(session: ProjectSession): ChatThread? {
        val id = session.activeThreadId.value
        return session.threads.value.firstOrNull { it.id == id }
    }

    private fun activeMessages(session: ProjectSession): List<ChatMessage> {
        val id = session.activeThreadId.value
        return session.threads.value.firstOrNull { it.id == id }?.messages ?: emptyList()
    }

    private fun appendUserMessage(session: ProjectSession, text: String) {
        session.updateActiveMessages { it + ChatMessage(id = newMessageId(), role = ChatRole.USER, text = text, timestamp = now()) }
    }

    private fun appendAiMessage(session: ProjectSession, text: String, codeSnippet: ChatCodeSnippet? = null) {
        session.updateActiveMessages {
            it + ChatMessage(id = newMessageId(), role = ChatRole.AI, text = text, codeSnippet = codeSnippet, timestamp = now())
        }
    }

    private fun appendToolMessage(session: ProjectSession, toolCall: ChatToolCall) {
        session.updateActiveMessages {
            it + ChatMessage(id = newMessageId(), role = ChatRole.AI, text = "", timestamp = now(), toolCall = toolCall)
        }
    }

    private fun updateToolCall(session: ProjectSession, toolCallId: String, status: ToolCallStatus, resultText: String?) {
        session.updateActiveMessages { list ->
            list.map { message ->
                val call = message.toolCall
                if (call?.id == toolCallId) {
                    message.copy(toolCall = call.copy(status = status, resultText = resultText ?: call.resultText))
                } else {
                    message
                }
            }
        }
    }

    private fun coalesce(turns: List<LlmChatTurn>): List<LlmChatTurn> {
        val merged = ArrayList<LlmChatTurn>()
        for (turn in turns) {
            val last = merged.lastOrNull()
            if (last != null && last.role == turn.role) {
                merged[merged.lastIndex] = LlmChatTurn(turn.role, "${last.text}\n\n${turn.text}")
            } else {
                merged.add(turn)
            }
        }
        return merged
    }

    private fun formatResults(results: List<AgentToolResult>): String = buildString {
        appendLine("TOOL RESULTS:")
        results.forEach { result ->
            val status = if (result.ok) "OK" else "ERROR"
            appendLine("[${result.action.tool} ${result.action.pathOrNull() ?: ""}] $status:")
            appendLine(result.output.take(MAX_MODEL_OUTPUT))
        }
    }

    private fun newMessageId(): String = UUID.randomUUID().toString()

    private fun now(): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

    private fun newThread(): ChatThread {
        val timestamp = System.currentTimeMillis()
        return ChatThread(
            id = UUID.randomUUID().toString(),
            title = "",
            createdAt = timestamp,
            updatedAt = timestamp,
            messages = listOf(welcomeMessage()),
        )
    }

    private fun AgentAction.pathOrNull(): String? = when (this) {
        is AgentAction.Search -> null
        is AgentAction.ListDir -> path
        is AgentAction.ReadFile -> path
        is AgentAction.CreateFile -> path
        is AgentAction.CreateDir -> path
        is AgentAction.EditFile -> path
        is AgentAction.Rename -> path
        is AgentAction.Move -> path
        is AgentAction.Delete -> path
    }

    private fun AgentAction.summary(): String = when (this) {
        is AgentAction.ListDir -> "List $path"
        is AgentAction.ReadFile -> "Read $path"
        is AgentAction.Search -> "Search \"$query\""
        is AgentAction.CreateFile -> "Create $path"
        is AgentAction.CreateDir -> "New folder $path"
        is AgentAction.EditFile -> "Edit $path"
        is AgentAction.Rename -> "Rename $path -> $newName"
        is AgentAction.Move -> "Move $path -> $newParent"
        is AgentAction.Delete -> "Delete $path"
    }

    private companion object {
        const val WELCOME_ID = "welcome"
        const val UNTITLED = "New chat"
        const val MAX_ITERATIONS = 12
        const val MAX_CARD_OUTPUT = 4000
        const val MAX_MODEL_OUTPUT = 24000

        fun welcomeMessage() = ChatMessage(
            id = WELCOME_ID,
            role = ChatRole.AI,
            text = "Hi! I'm the Android Studio Lite agent. I can read and edit files in this project — " +
                "ask me to create a screen, fix a bug, or refactor something.",
            timestamp = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()),
        )
    }
}

/** The configured default provider if valid, else the first provider (catalog order) with a validated key. */
internal fun AiAgentSettings.activeProvider(): AiProviderConfig? {
    if (!enabled) return null
    val byId = providers.associateBy { it.id }
    val valid = AiProviderCatalog.all.mapNotNull { byId[it.id] }.filter { it.status == ApiKeyStatus.VALID }
    return valid.firstOrNull { it.id == activeProviderId } ?: valid.firstOrNull()
}
