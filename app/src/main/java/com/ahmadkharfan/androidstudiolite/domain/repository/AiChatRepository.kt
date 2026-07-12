package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface AiChatRepository {
    fun observeMessages(): Flow<List<ChatMessage>>
    suspend fun sendMessage(text: String)
    suspend fun markApplied(messageId: String)
}
