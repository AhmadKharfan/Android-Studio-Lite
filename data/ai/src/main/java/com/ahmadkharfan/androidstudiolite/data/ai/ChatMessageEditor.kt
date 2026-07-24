package com.ahmadkharfan.androidstudiolite.data.ai

import com.ahmadkharfan.androidstudiolite.domain.model.ChatCodeSnippet
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMessage
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMessageKind
import com.ahmadkharfan.androidstudiolite.domain.model.ChatRole
import com.ahmadkharfan.androidstudiolite.domain.model.ChatThread
import com.ahmadkharfan.androidstudiolite.domain.model.ChatToolCall
import com.ahmadkharfan.androidstudiolite.domain.model.ToolCallStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

internal fun interface ChatIdGenerator {
    fun newId(): String
}

internal fun interface ChatClock {
    fun timeLabel(): String
}

private val defaultIds = ChatIdGenerator { UUID.randomUUID().toString() }
private val defaultClock = ChatClock { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) }

internal class ChatMessageEditor(
    private val ids: ChatIdGenerator = defaultIds,
    private val clock: ChatClock = defaultClock,
) {
    fun newMessageId(): String = ids.newId()

    fun newThread(): ChatThread {
        val timestamp = System.currentTimeMillis()
        return ChatThread(
            id = ids.newId(),
            title = "",
            createdAt = timestamp,
            updatedAt = timestamp,
            messages = listOf(welcomeMessage()),
        )
    }

    fun appendUserMessage(session: ChatSession, text: String) {
        session.updateActiveMessages { it + ChatMessage(id = ids.newId(), role = ChatRole.USER, text = text, timestamp = clock.timeLabel()) }
    }

    fun appendAiMessage(session: ChatSession, text: String, codeSnippet: ChatCodeSnippet? = null) {
        session.updateActiveMessages {
            it + ChatMessage(id = ids.newId(), role = ChatRole.AI, text = text, codeSnippet = codeSnippet, timestamp = clock.timeLabel())
        }
    }

    fun appendPlanMessage(session: ChatSession, text: String) {
        session.updateActiveMessages { list ->
            list.map { message ->
                if (message.showPlanActions) message.copy(showPlanActions = false) else message
            } + ChatMessage(
                id = ids.newId(),
                role = ChatRole.AI,
                text = text,
                timestamp = clock.timeLabel(),
                showPlanActions = true,
            )
        }
    }

    fun appendToolMessage(session: ChatSession, toolCall: ChatToolCall) {
        session.updateActiveMessages {
            it + ChatMessage(id = ids.newId(), role = ChatRole.AI, text = "", timestamp = clock.timeLabel(), toolCall = toolCall)
        }
    }

    fun appendStreamingMessage(session: ChatSession, id: String, kind: ChatMessageKind, text: String) {
        session.updateActiveMessages {
            it + ChatMessage(id = id, role = ChatRole.AI, text = text, timestamp = clock.timeLabel(), kind = kind, streaming = true)
        }
    }

    fun updateMessageText(session: ChatSession, id: String, text: String, streaming: Boolean) {
        session.updateActiveMessages { list ->
            list.map { if (it.id == id) it.copy(text = text, streaming = streaming) else it }
        }
    }

    fun removeMessage(session: ChatSession, id: String) {
        session.updateActiveMessages { list -> list.filterNot { it.id == id } }
    }

    fun updateToolCall(session: ChatSession, toolCallId: String, status: ToolCallStatus, resultText: String?) {
        session.updateActiveMessages { list ->
            list.map { message ->
                val call = message.toolCall
                if (call?.id == toolCallId) {
                    message.copy(toolCall = call.copy(status = status, resultText = resultText ?: call.resultText))
                } else {
                    message
                }
            }
        }
    }

    fun finalizeThinking(session: ChatSession, thoughtId: String, keep: Boolean) {
        val text = session.activeMessages().firstOrNull { it.id == thoughtId }?.text.orEmpty()
        if (!keep || text.isBlank()) {
            removeMessage(session, thoughtId)
        } else {
            updateMessageText(session, thoughtId, text, streaming = false)
        }
    }

    private fun welcomeMessage() = ChatMessage(
        id = WELCOME_ID,
        role = ChatRole.AI,
        text = "Hi! I'm the Android Studio Lite agent. I can read and edit files in this project. " +
            "Ask me to create a screen, fix a bug, or refactor something.",
        timestamp = clock.timeLabel(),
    )
}
