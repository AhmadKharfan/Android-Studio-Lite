package com.ahmadkharfan.androidstudiolite.data.ai

import com.ahmadkharfan.androidstudiolite.domain.model.ChatMode
import com.ahmadkharfan.androidstudiolite.domain.model.ChatThread
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ChatSessionStore(
    private val historyStore: ChatHistory,
    private val newThread: () -> ChatThread,
) {
    private val sessions = HashMap<String, ChatSession>()
    private val mutex = Mutex()

    suspend fun sessionFor(projectId: String): ChatSession = mutex.withLock {
        sessions[projectId]?.let { return it }
        val saved = historyStore.load(projectId)
        val session = ChatSession(projectId)
        if (saved.threads.isEmpty()) {
            val thread = newThread()
            session.threads.value = listOf(thread)
            session.activeThreadId.value = thread.id
        } else {
            session.threads.value = saved.threads
            session.activeThreadId.value = saved.activeThreadId.ifBlank { saved.threads.maxByOrNull { it.updatedAt }!!.id }
        }
        sessions[projectId] = session
        session
    }

    suspend fun persist(session: ChatSession) {
        historyStore.save(session.projectId, ProjectChats(session.activeThreadId.value, session.threads.value))
    }

    suspend fun newChat(projectId: String) {
        val session = sessionFor(projectId)
        val thread = newThread()
        session.threads.value = session.threads.value + thread
        session.activeThreadId.value = thread.id
        persist(session)
    }

    suspend fun selectThread(projectId: String, threadId: String) {
        val session = sessionFor(projectId)
        if (session.threads.value.any { it.id == threadId }) {
            session.activeThreadId.value = threadId
            persist(session)
        }
    }

    suspend fun deleteThread(projectId: String, threadId: String) {
        val session = sessionFor(projectId)
        val remaining = session.threads.value.filterNot { it.id == threadId }
        if (remaining.isEmpty()) {
            val thread = newThread()
            session.threads.value = listOf(thread)
            session.activeThreadId.value = thread.id
        } else {
            session.threads.value = remaining
            if (session.activeThreadId.value == threadId) {
                session.activeThreadId.value = remaining.maxByOrNull { it.updatedAt }!!.id
            }
        }
        persist(session)
    }

    suspend fun setThreadMode(projectId: String, threadId: String, mode: ChatMode) {
        val session = sessionFor(projectId)
        session.updateThreadById(threadId) { it.copy(mode = mode) }
        persist(session)
    }

    suspend fun setThreadModelSelection(projectId: String, threadId: String, providerId: String, model: String) {
        val session = sessionFor(projectId)
        session.updateThreadById(threadId) { it.copy(providerId = providerId, model = model) }
        persist(session)
    }
}
