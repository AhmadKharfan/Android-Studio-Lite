package com.example.androidstudiolite.feature.editor.aichat.uiState

data class ChatMessageUiModel(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val timestamp: String,
    val codeLanguage: String? = null,
    val code: String? = null,
    val applied: Boolean = false,
)

data class AiChatUiState(
    val hasConfiguredProvider: Boolean = true,
    val messages: List<ChatMessageUiModel> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
)
