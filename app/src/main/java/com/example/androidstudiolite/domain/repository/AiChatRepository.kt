package com.example.androidstudiolite.domain.repository

import com.example.androidstudiolite.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface AiChatRepository {
    fun observeMessages(): Flow<List<ChatMessage>>
    suspend fun sendMessage(text: String)
    suspend fun markApplied(messageId: String)
}
