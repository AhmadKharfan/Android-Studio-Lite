package com.example.androidstudiolite.data.fake

import com.example.androidstudiolite.domain.model.ChatCodeSnippet
import com.example.androidstudiolite.domain.model.ChatMessage
import com.example.androidstudiolite.domain.model.ChatRole
import com.example.androidstudiolite.domain.repository.AiChatRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeAiChatRepository : AiChatRepository {

    private val messages = MutableStateFlow(
        listOf(
            ChatMessage(
                id = "1",
                role = ChatRole.AI,
                text = "Hi! I'm the Android Studio Lite AI agent. Ask me to explain code, fix a bug, or generate a snippet.",
                timestamp = now(),
            ),
        ),
    )
    private var nextId = 2

    override fun observeMessages(): StateFlow<List<ChatMessage>> = messages

    override suspend fun sendMessage(text: String) {
        val userMessage = ChatMessage(id = (nextId++).toString(), role = ChatRole.USER, text = text, timestamp = now())
        messages.value = messages.value + userMessage
        delay(900)
        messages.value = messages.value + craftReply(text)
    }

    override suspend fun markApplied(messageId: String) {
        messages.value = messages.value.map { if (it.id == messageId) it.copy(applied = true) else it }
    }

    private fun craftReply(prompt: String): ChatMessage {
        val id = (nextId++).toString()
        val wantsCode = listOf("fun", "code", "greet", "function").any { prompt.contains(it, ignoreCase = true) }
        return if (wantsCode) {
            ChatMessage(
                id = id,
                role = ChatRole.AI,
                text = "Here's a small Kotlin function for that:",
                codeSnippet = ChatCodeSnippet(
                    language = "kotlin",
                    code = "fun greet(name: String): String {\n    return \"Hello, \$name!\"\n}",
                ),
                timestamp = now(),
            )
        } else {
            ChatMessage(
                id = id,
                role = ChatRole.AI,
                text = "Got it — noted. This preview build replies with canned suggestions rather than a live model.",
                timestamp = now(),
            )
        }
    }

    private fun now(): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
}
