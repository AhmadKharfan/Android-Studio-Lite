package com.example.androidstudiolite.feature.editor.aichat.interaction

sealed interface AiChatInteraction {
    data class InputChanged(val value: String) : AiChatInteraction
    data object Send : AiChatInteraction
    data class MarkApplied(val messageId: String) : AiChatInteraction
}
