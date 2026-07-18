package com.ahmadkharfan.androidstudiolite.data.ai

import com.ahmadkharfan.androidstudiolite.domain.model.AiAgentSettings
import com.ahmadkharfan.androidstudiolite.domain.model.AiProviderConfig
import com.ahmadkharfan.androidstudiolite.domain.model.ApiKeyStatus
import com.ahmadkharfan.androidstudiolite.domain.model.ChatCodeSnippet
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMessage
import com.ahmadkharfan.androidstudiolite.domain.model.ChatRole
import com.ahmadkharfan.androidstudiolite.domain.repository.AiAgentRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.AiChatRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

class RealAiChatRepository(
    private val aiAgentRepository: AiAgentRepository,
    private val keyStore: EncryptedAiKeyStore,
    private val gateway: AiLlmGateway,
) : AiChatRepository {

    private val messages = MutableStateFlow<List<ChatMessage>>(listOf(welcomeMessage()))
    private var nextId = 2L

    override fun observeMessages(): StateFlow<List<ChatMessage>> = messages

    override suspend fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val settings = aiAgentRepository.observeSettings().first()
        val userMessage = ChatMessage(
            id = nextId().toString(),
            role = ChatRole.USER,
            text = trimmed,
            timestamp = now(),
        )
        messages.value = messages.value + userMessage

        val provider = settings.activeProvider()
        if (provider == null) {
            appendAiMessage("No validated AI provider. Add an API key in Settings and tap Test.")
            return
        }
        val apiKey = keyStore.getKey(provider.id)
        if (apiKey.isBlank()) {
            appendAiMessage("API key missing for ${provider.name}. Re-enter it in Settings.")
            return
        }

        val history = messages.value
            .dropLast(1)
            .filter { it.id != WELCOME_ID }
            .map { LlmChatTurn(it.role, it.text) }

        val reply = runCatching {
            gateway.chat(
                providerId = provider.id,
                apiKey = apiKey,
                systemPrompt = settings.instructions,
                history = history,
                userMessage = trimmed,
            )
        }.getOrElse { error ->
            val detail = (error as? AiLlmException)?.message ?: error.message ?: "Unknown error"
            LlmReply(text = "Request failed: $detail")
        }

        appendAiMessage(reply.text, reply.codeSnippet)
    }

    private fun appendAiMessage(text: String, codeSnippet: ChatCodeSnippet? = null) {
        messages.value = messages.value + ChatMessage(
            id = nextId().toString(),
            role = ChatRole.AI,
            text = text,
            codeSnippet = codeSnippet,
            timestamp = now(),
        )
    }

    override suspend fun markApplied(messageId: String) {
        messages.value = messages.value.map { if (it.id == messageId) it.copy(applied = true) else it }
    }

    private fun nextId(): Long = nextId++

    private fun now(): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

    private companion object {
        const val WELCOME_ID = "welcome"

        fun welcomeMessage() = ChatMessage(
            id = WELCOME_ID,
            role = ChatRole.AI,
            text = "Hi! I'm the Android Studio Lite AI agent. Ask me to explain code, fix a bug, or generate a snippet.",
            timestamp = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()),
        )
    }
}

/** First provider in catalog order with a validated key — used for chat requests. */
internal fun AiAgentSettings.activeProvider(): AiProviderConfig? {
    if (!enabled) return null
    val byId = providers.associateBy { it.id }
    return AiProviderCatalog.all
        .mapNotNull { byId[it.id] }
        .firstOrNull { it.status == ApiKeyStatus.VALID }
}
