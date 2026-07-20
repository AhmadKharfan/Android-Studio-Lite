package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.ChatMessage
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMode
import com.ahmadkharfan.androidstudiolite.domain.model.ChatThreadSelection
import com.ahmadkharfan.androidstudiolite.domain.model.ChatThreadSummary
import kotlinx.coroutines.flow.Flow

interface AiChatRepository {
    fun observeMessages(projectId: String): Flow<List<ChatMessage>>

    fun observeThreads(projectId: String): Flow<List<ChatThreadSummary>>

    fun observeActiveThreadId(projectId: String): Flow<String>

    fun observeActiveSelection(projectId: String): Flow<ChatThreadSelection>

    suspend fun sendMessage(text: String, projectId: String, activeFilePath: String? = null)

    suspend fun markApplied(projectId: String, messageId: String)

    suspend fun newChat(projectId: String)

    suspend fun selectThread(projectId: String, threadId: String)

    suspend fun deleteThread(projectId: String, threadId: String)

    suspend fun setThreadMode(projectId: String, threadId: String, mode: ChatMode)

    suspend fun setThreadModelSelection(projectId: String, threadId: String, providerId: String, model: String)

    suspend fun buildFromPlan(projectId: String, planMessageId: String, activeFilePath: String? = null)

    suspend fun reviewPlan(
        projectId: String,
        planMessageId: String,
        activeFilePath: String? = null,
        userInstructions: String? = null,
    )

    fun approveTool(toolCallId: String)

    fun rejectTool(toolCallId: String)
}
