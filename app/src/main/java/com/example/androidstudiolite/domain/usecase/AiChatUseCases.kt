package com.example.androidstudiolite.domain.usecase

import com.example.androidstudiolite.domain.model.ChatMessage
import com.example.androidstudiolite.domain.repository.AiChatRepository
import kotlinx.coroutines.flow.Flow

class ObserveChatMessagesUseCase(private val repository: AiChatRepository) {
    operator fun invoke(): Flow<List<ChatMessage>> = repository.observeMessages()
}

class SendChatMessageUseCase(private val repository: AiChatRepository) {
    suspend operator fun invoke(text: String) = repository.sendMessage(text)
}

class MarkChatMessageAppliedUseCase(private val repository: AiChatRepository) {
    suspend operator fun invoke(messageId: String) = repository.markApplied(messageId)
}
