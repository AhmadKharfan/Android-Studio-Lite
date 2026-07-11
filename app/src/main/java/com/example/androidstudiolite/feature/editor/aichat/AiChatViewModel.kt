package com.example.androidstudiolite.feature.editor.aichat

import com.example.androidstudiolite.core.BaseViewModel
import com.example.androidstudiolite.domain.model.ApiKeyStatus
import com.example.androidstudiolite.domain.model.ChatMessage
import com.example.androidstudiolite.domain.model.ChatRole
import com.example.androidstudiolite.domain.repository.AiAgentRepository
import com.example.androidstudiolite.domain.repository.AiChatRepository

class AiChatViewModel(
    private val aiChatRepository: AiChatRepository,
    private val aiAgentRepository: AiAgentRepository,
) : BaseViewModel<AiChatUiState, Nothing>(
    initialState = AiChatUiState(),
), AiChatInteractionListener {

    init {
        tryToCollect(
            block = { aiChatRepository.observeMessages() },
            onCollect = { messages ->
                updateState { copy(messages = messages.map { m -> m.toUiModel() }) }
            },
        )
        tryToCollect(
            block = { aiAgentRepository.observeSettings() },
            onCollect = { settings ->
                val configured = settings.enabled && settings.providers.any { it.status == ApiKeyStatus.VALID }
                updateState { copy(hasConfiguredProvider = configured) }
            },
        )
    }

    override fun onInputChanged(value: String) {
        updateState { copy(input = value) }
    }

    override fun onSend() {
        val text = state.value.input.trim()
        if (text.isEmpty() || state.value.sending) return
        updateState { copy(input = "", sending = true) }
        tryToExecute(
            block = { aiChatRepository.sendMessage(text) },
            onSuccess = { updateState { copy(sending = false) } },
        )
    }

    override fun onMarkApplied(messageId: String) {
        tryToExecute(block = { aiChatRepository.markApplied(messageId) })
    }

    private fun ChatMessage.toUiModel() = ChatMessageUiModel(
        id = id,
        isUser = role == ChatRole.USER,
        text = text,
        timestamp = timestamp,
        codeLanguage = codeSnippet?.language,
        code = codeSnippet?.code,
        applied = applied,
    )
}
