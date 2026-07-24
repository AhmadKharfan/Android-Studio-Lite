package com.ahmadkharfan.androidstudiolite.data.ai

import com.ahmadkharfan.androidstudiolite.domain.model.ChatMessage
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMode
import com.ahmadkharfan.androidstudiolite.domain.model.ChatThread
import com.ahmadkharfan.androidstudiolite.domain.model.ChatThreadSelection
import com.ahmadkharfan.androidstudiolite.domain.model.ChatThreadSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

internal const val WELCOME_ID = "welcome"
internal const val UNTITLED = "New chat"

internal class ChatSession(val projectId: String) {
    val threads = MutableStateFlow<List<ChatThread>>(emptyList())
    val activeThreadId = MutableStateFlow("")

    fun messages(): Flow<List<ChatMessage>> = combine(threads, activeThreadId) { list, id ->
        list.firstOrNull { it.id == id }?.messages ?: emptyList()
    }

    fun summaries(): Flow<List<ChatThreadSummary>> = threads.map { list ->
        list.sortedByDescending { it.updatedAt }
            .map { ChatThreadSummary(it.id, it.title.ifBlank { UNTITLED }, it.updatedAt, it.messages.count { m -> m.id != WELCOME_ID }) }
    }

    fun selection(): Flow<ChatThreadSelection> = combine(threads, activeThreadId) { list, id ->
        val thread = list.firstOrNull { it.id == id }
        ChatThreadSelection(id, thread?.mode ?: ChatMode.AGENT, thread?.providerId, thread?.model)
    }

    fun activeThread(): ChatThread? = threads.value.firstOrNull { it.id == activeThreadId.value }

    fun activeMessages(): List<ChatMessage> = activeThread()?.messages ?: emptyList()

    fun updateActiveMessages(transform: (List<ChatMessage>) -> List<ChatMessage>) {
        val id = activeThreadId.value
        threads.value = threads.value.map { thread ->
            if (thread.id == id) {
                thread.copy(messages = transform(thread.messages), updatedAt = System.currentTimeMillis())
            } else {
                thread
            }
        }
    }

    fun setTitleIfBlank(title: String) {
        val id = activeThreadId.value
        threads.value = threads.value.map { thread ->
            if (thread.id == id && thread.title.isBlank()) thread.copy(title = title) else thread
        }
    }

    fun updateThreadById(threadId: String, transform: (ChatThread) -> ChatThread) {
        threads.value = threads.value.map { thread -> if (thread.id == threadId) transform(thread) else thread }
    }
}
