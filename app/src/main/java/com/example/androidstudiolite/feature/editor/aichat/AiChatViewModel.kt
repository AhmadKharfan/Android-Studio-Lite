package com.example.androidstudiolite.feature.editor.aichat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.domain.model.ApiKeyStatus
import com.example.androidstudiolite.domain.model.ChatMessage
import com.example.androidstudiolite.domain.model.ChatRole
import com.example.androidstudiolite.domain.repository.AiAgentRepository
import com.example.androidstudiolite.domain.repository.AiChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AiChatViewModel(
    private val aiChatRepository: AiChatRepository,
    private val aiAgentRepository: AiAgentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            aiChatRepository.observeMessages().collect { messages ->
                _uiState.update { it.copy(messages = messages.map { m -> m.toUiModel() }) }
            }
        }
        viewModelScope.launch {
            aiAgentRepository.observeSettings().collect { settings ->
                val configured = settings.enabled && settings.providers.any { it.status == ApiKeyStatus.VALID }
                _uiState.update { it.copy(hasConfiguredProvider = configured) }
            }
        }
    }

    fun onInteraction(interaction: AiChatInteraction) {
        when (interaction) {
            is AiChatInteraction.InputChanged -> _uiState.update { it.copy(input = interaction.value) }
            AiChatInteraction.Send -> send()
            is AiChatInteraction.MarkApplied -> viewModelScope.launch { aiChatRepository.markApplied(interaction.messageId) }
        }
    }

    private fun send() {
        val text = _uiState.value.input.trim()
        if (text.isEmpty() || _uiState.value.sending) return
        _uiState.update { it.copy(input = "", sending = true) }
        viewModelScope.launch {
            aiChatRepository.sendMessage(text)
            _uiState.update { it.copy(sending = false) }
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
    )
}
