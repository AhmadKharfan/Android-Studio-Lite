package com.ahmadkharfan.androidstudiolite.data.ai

import com.ahmadkharfan.androidstudiolite.domain.model.ChatMessageKind
import com.ahmadkharfan.androidstudiolite.domain.model.ChatRole
import com.ahmadkharfan.androidstudiolite.domain.model.ChatThread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageEditorTest {

    private var counter = 0
    private val editor = ChatMessageEditor(
        ids = ChatIdGenerator { "id-${++counter}" },
        clock = ChatClock { "12:00 PM" },
    )

    private fun sessionWithThread(): ChatSession = ChatSession("p").apply {
        threads.value = listOf(ChatThread(id = "t", title = "", createdAt = 0, updatedAt = 0, messages = emptyList()))
        activeThreadId.value = "t"
    }

    @Test
    fun appendUserMessageAddsUserMessageWithGeneratedIdAndTimestamp() {
        val session = sessionWithThread()
        editor.appendUserMessage(session, "hello")

        val message = session.activeMessages().single()
        assertEquals(ChatRole.USER, message.role)
        assertEquals("hello", message.text)
        assertEquals("id-1", message.id)
        assertEquals("12:00 PM", message.timestamp)
    }

    @Test
    fun appendPlanMessageClearsPlanActionsOnPreviousPlan() {
        val session = sessionWithThread()
        editor.appendPlanMessage(session, "plan A")
        editor.appendPlanMessage(session, "plan B")

        val plans = session.activeMessages()
        assertFalse(plans.first { it.text == "plan A" }.showPlanActions)
        assertTrue(plans.first { it.text == "plan B" }.showPlanActions)
    }

    @Test
    fun updateAndRemoveMessageEditActiveThread() {
        val session = sessionWithThread()
        editor.appendStreamingMessage(session, "m1", ChatMessageKind.THINKING, "partial")
        editor.updateMessageText(session, "m1", "complete", streaming = false)
        assertEquals("complete", session.activeMessages().single { it.id == "m1" }.text)
        assertFalse(session.activeMessages().single { it.id == "m1" }.streaming)

        editor.removeMessage(session, "m1")
        assertNull(session.activeMessages().firstOrNull { it.id == "m1" })
    }

    @Test
    fun newThreadStartsWithGeneratedIdAndWelcomeMessage() {
        val thread = editor.newThread()
        assertEquals("id-1", thread.id)
        assertEquals(WELCOME_ID, thread.messages.single().id)
        assertEquals("12:00 PM", thread.messages.single().timestamp)
    }
}
