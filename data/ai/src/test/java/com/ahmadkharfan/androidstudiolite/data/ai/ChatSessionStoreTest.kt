package com.ahmadkharfan.androidstudiolite.data.ai

import com.ahmadkharfan.androidstudiolite.domain.model.ChatMode
import com.ahmadkharfan.androidstudiolite.domain.model.ChatThread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class InMemoryChatHistory(private val stored: MutableMap<String, ProjectChats> = mutableMapOf()) : ChatHistory {
    override suspend fun load(projectId: String): ProjectChats = stored[projectId] ?: ProjectChats("", emptyList())
    override suspend fun save(projectId: String, data: ProjectChats) { stored[projectId] = data }
    fun saved(projectId: String): ProjectChats? = stored[projectId]
}

class ChatSessionStoreTest {

    private var counter = 0
    private fun newThread(): ChatThread {
        counter++
        return ChatThread(id = "thread-$counter", title = "", createdAt = counter.toLong(), updatedAt = counter.toLong(), messages = emptyList())
    }

    private fun store(history: ChatHistory) = ChatSessionStore(history) { newThread() }

    @Test
    fun sessionForSeedsAnInitialThreadWhenHistoryIsEmpty() = runBlocking {
        val session = store(InMemoryChatHistory()).sessionFor("p")
        assertEquals(1, session.threads.value.size)
        assertEquals(session.threads.value.single().id, session.activeThreadId.value)
    }

    @Test
    fun sessionForRestoresSavedThreadsAndActiveSelection() = runBlocking {
        val saved = ProjectChats(
            activeThreadId = "t2",
            threads = listOf(
                ChatThread("t1", "", 1, 1, emptyList()),
                ChatThread("t2", "", 2, 2, emptyList()),
            ),
        )
        val session = store(InMemoryChatHistory(mutableMapOf("p" to saved))).sessionFor("p")
        assertEquals(listOf("t1", "t2"), session.threads.value.map { it.id })
        assertEquals("t2", session.activeThreadId.value)
    }

    @Test
    fun newChatAddsAnActiveThreadAndPersists() = runBlocking {
        val history = InMemoryChatHistory()
        val store = store(history)
        store.sessionFor("p")
        store.newChat("p")

        val session = store.sessionFor("p")
        assertEquals(2, session.threads.value.size)
        assertEquals(session.threads.value.last().id, session.activeThreadId.value)
        assertEquals(session.activeThreadId.value, history.saved("p")?.activeThreadId)
    }

    @Test
    fun deletingTheLastThreadSeedsAFreshOne() = runBlocking {
        val store = store(InMemoryChatHistory())
        val only = store.sessionFor("p").activeThreadId.value

        store.deleteThread("p", only)

        val session = store.sessionFor("p")
        assertEquals(1, session.threads.value.size)
        assertNotEquals(only, session.threads.value.single().id)
    }

    @Test
    fun setThreadModePersistsTheChange() = runBlocking {
        val history = InMemoryChatHistory()
        val store = store(history)
        val threadId = store.sessionFor("p").activeThreadId.value

        store.setThreadMode("p", threadId, ChatMode.ASK)

        assertTrue(history.saved("p")!!.threads.single { it.id == threadId }.mode == ChatMode.ASK)
    }
}
