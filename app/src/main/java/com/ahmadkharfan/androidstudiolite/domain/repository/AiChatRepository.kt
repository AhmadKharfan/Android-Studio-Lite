package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.ChatMessage
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMode
import com.ahmadkharfan.androidstudiolite.domain.model.ChatThreadSelection
import com.ahmadkharfan.androidstudiolite.domain.model.ChatThreadSummary
import kotlinx.coroutines.flow.Flow

interface AiChatRepository {
    /** Messages of the active thread for [projectId]. */
    fun observeMessages(projectId: String): Flow<List<ChatMessage>>

    /** All saved threads for [projectId], most recently used first. */
    fun observeThreads(projectId: String): Flow<List<ChatThreadSummary>>

    /** Id of the active thread for [projectId]. */
    fun observeActiveThreadId(projectId: String): Flow<String>

    /** Mode + provider/model of the active thread for [projectId]. */
    fun observeActiveSelection(projectId: String): Flow<ChatThreadSelection>

    /** Runs the agent for [text] against the open project; [activeFilePath] hints the currently open file. */
    suspend fun sendMessage(text: String, projectId: String, activeFilePath: String? = null)

    suspend fun markApplied(projectId: String, messageId: String)

    /** Starts a fresh thread for [projectId] and makes it active. */
    suspend fun newChat(projectId: String)

    /** Switches the active thread for [projectId]. */
    suspend fun selectThread(projectId: String, threadId: String)

    /** Removes a thread; if it was active another is selected (or a fresh one created). */
    suspend fun deleteThread(projectId: String, threadId: String)

    /** Sets the conversation mode (Agent/Ask/Plan) for a thread. */
    suspend fun setThreadMode(projectId: String, threadId: String, mode: ChatMode)

    /** Sets the provider + model for a thread and remembers it as the global default. */
    suspend fun setThreadModelSelection(projectId: String, threadId: String, providerId: String, model: String)

    /** Approves a pending tool call, resuming the paused agent loop. */
    fun approveTool(toolCallId: String)

    /** Rejects a pending tool call; the agent is told and may adapt. */
    fun rejectTool(toolCallId: String)
}
