package com.ahmadkharfan.androidstudiolite.feature.editor.aichat

import androidx.lifecycle.viewModelScope
import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.domain.model.AiAgentSettings
import com.ahmadkharfan.androidstudiolite.domain.model.ApiKeyStatus
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMessage
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMode
import com.ahmadkharfan.androidstudiolite.domain.model.ChatRole
import com.ahmadkharfan.androidstudiolite.domain.model.ChatThreadSelection
import com.ahmadkharfan.androidstudiolite.domain.model.ChatThreadSummary
import com.ahmadkharfan.androidstudiolite.domain.repository.AiAgentRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.AiChatRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AiChatViewModel(
    private val aiChatRepository: AiChatRepository,
    private val aiAgentRepository: AiAgentRepository,
    private val projectId: String,
) : BaseViewModel<AiChatUiState, Nothing>(
    initialState = AiChatUiState(),
), AiChatInteractionListener {

    private var activeFilePath: String? = null

    init {
        tryToCollect(
            block = { aiChatRepository.observeMessages(projectId) },
            onCollect = { messages ->
                updateState { copy(messages = messages.map { m -> m.toUiModel() }) }
            },
        )
        tryToCollect(
            block = {
                combine(
                    aiChatRepository.observeThreads(projectId),
                    aiChatRepository.observeActiveThreadId(projectId),
                ) { threads, activeId -> threads.map { it.toUiModel(activeId) } }
            },
            onCollect = { threads -> updateState { copy(threads = threads) } },
        )
        tryToCollect(
            block = { aiAgentRepository.observeSettings() },
            onCollect = { settings ->
                val configured = settings.enabled && settings.providers.any { it.status == ApiKeyStatus.VALID }
                updateState { copy(hasConfiguredProvider = configured, autoApply = settings.autoApply) }
            },
        )
        tryToCollect(
            block = {
                combine(
                    aiChatRepository.observeActiveSelection(projectId),
                    aiAgentRepository.observeSettings(),
                ) { selection, settings -> selection to settings }
            },
            onCollect = { (selection, settings) -> applySelection(selection, settings) },
        )
    }

    private fun applySelection(selection: ChatThreadSelection, settings: AiAgentSettings) {
        val validated = settings.providers.filter { it.status == ApiKeyStatus.VALID }
        val providers = validated.map { ChatProviderUiModel(it.id, it.name, it.availableModels) }
        val providerId = selection.providerId?.takeIf { id -> validated.any { it.id == id } }
            ?: settings.activeProviderId.takeIf { id -> validated.any { it.id == id } }
            ?: validated.firstOrNull()?.id
            ?: ""
        val provider = validated.firstOrNull { it.id == providerId }
        val model = selection.model?.takeIf { it.isNotBlank() } ?: provider?.selectedModel ?: ""
        updateState {
            copy(
                mode = selection.mode,
                activeThreadId = selection.threadId,
                providerId = providerId,
                model = model,
                providers = providers,
            )
        }
    }

    fun onActiveFileChanged(path: String?) {
        activeFilePath = path
    }

    override fun onInputChanged(value: String) {
        updateState { copy(input = value) }
    }

    override fun onSend() {
        val text = state.value.input.trim()
        if (text.isEmpty() || state.value.sending) return
        updateState { copy(input = "", sending = true) }
        tryToExecute(
            block = { aiChatRepository.sendMessage(text, projectId, activeFilePath) },
            onSuccess = { updateState { copy(sending = false) } },
            onError = { updateState { copy(sending = false) } },
        )
    }

    override fun onMarkApplied(messageId: String) {
        tryToExecute(block = { aiChatRepository.markApplied(projectId, messageId) })
    }

    override fun onApproveTool(toolCallId: String) {
        aiChatRepository.approveTool(toolCallId)
    }

    override fun onRejectTool(toolCallId: String) {
        aiChatRepository.rejectTool(toolCallId)
    }

    override fun onToggleAutoApply(enabled: Boolean) {
        viewModelScope.launch { aiAgentRepository.setAutoApply(enabled) }
    }

    override fun onNewChat() {
        updateState { copy(showHistory = false) }
        tryToExecute(block = { aiChatRepository.newChat(projectId) })
    }

    override fun onToggleHistory() {
        updateState { copy(showHistory = !showHistory) }
    }

    override fun onSelectThread(threadId: String) {
        updateState { copy(showHistory = false) }
        tryToExecute(block = { aiChatRepository.selectThread(projectId, threadId) })
    }

    override fun onDeleteThread(threadId: String) {
        tryToExecute(block = { aiChatRepository.deleteThread(projectId, threadId) })
    }

    override fun onOpenControls() {
        updateState { copy(showControls = true) }
    }

    override fun onDismissControls() {
        updateState { copy(showControls = false) }
    }

    override fun onModeSelected(mode: ChatMode) {
        val threadId = state.value.activeThreadId.ifBlank { return }
        tryToExecute(block = { aiChatRepository.setThreadMode(projectId, threadId, mode) })
    }

    override fun onProviderSelected(providerId: String) {
        val threadId = state.value.activeThreadId.ifBlank { return }
        val model = state.value.providers.firstOrNull { it.id == providerId }?.models?.firstOrNull().orEmpty()
        tryToExecute(block = { aiChatRepository.setThreadModelSelection(projectId, threadId, providerId, model) })
    }

    override fun onModelSelected(model: String) {
        val threadId = state.value.activeThreadId.ifBlank { return }
        val providerId = state.value.providerId.ifBlank { return }
        tryToExecute(block = { aiChatRepository.setThreadModelSelection(projectId, threadId, providerId, model) })
    }

    override fun onRefreshModels() {
        val providerId = state.value.providerId.ifBlank { return }
        viewModelScope.launch { aiAgentRepository.refreshModels(providerId) }
    }

    override fun onPlanBuild(planMessageId: String) {
        if (state.value.sending) return
        updateState { copy(sending = true) }
        tryToExecute(
            block = { aiChatRepository.buildFromPlan(projectId, planMessageId, activeFilePath) },
            onSuccess = { updateState { copy(sending = false) } },
            onError = { updateState { copy(sending = false) } },
        )
    }

    override fun onPlanReview(planMessageId: String) {
        updateState { copy(planReviewMessageId = planMessageId, planReviewInput = "") }
    }

    override fun onPlanReviewInputChanged(value: String) {
        updateState { copy(planReviewInput = value) }
    }

    override fun onDismissPlanReview() {
        updateState { copy(planReviewMessageId = null, planReviewInput = "") }
    }

    override fun onSubmitPlanReview() {
        val planMessageId = state.value.planReviewMessageId ?: return
        if (state.value.sending) return
        val instructions = state.value.planReviewInput.trim().takeIf { it.isNotBlank() }
        updateState { copy(planReviewMessageId = null, planReviewInput = "", sending = true) }
        tryToExecute(
            block = { aiChatRepository.reviewPlan(projectId, planMessageId, activeFilePath, instructions) },
            onSuccess = { updateState { copy(sending = false) } },
            onError = { updateState { copy(sending = false) } },
        )
    }

    private fun ChatThreadSummary.toUiModel(activeId: String) = ChatThreadUiModel(
        id = id,
        title = title,
        subtitle = relativeTime(updatedAt),
        isActive = id == activeId,
    )

    private fun relativeTime(epochMillis: Long): String {
        val diff = System.currentTimeMillis() - epochMillis
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMillis))
        }
    }

    private fun ChatMessage.toUiModel() = ChatMessageUiModel(
        id = id,
        isUser = role == ChatRole.USER,
        text = text,
        timestamp = timestamp,
        codeLanguage = codeSnippet?.language,
        code = codeSnippet?.code,
        applied = applied,
        toolCall = toolCall?.let {
            ChatToolCallUiModel(
                id = it.id,
                tool = it.tool,
                path = it.path,
                summary = it.summary,
                diffOld = it.diffOld,
                diffNew = it.diffNew,
                status = it.status,
                resultText = it.resultText,
                mutating = it.mutating,
            )
        },
        kind = kind,
        streaming = streaming,
        showPlanActions = showPlanActions,
    )
}
