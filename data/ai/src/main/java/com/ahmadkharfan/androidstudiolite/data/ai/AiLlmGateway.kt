package com.ahmadkharfan.androidstudiolite.data.ai

import com.ahmadkharfan.androidstudiolite.data.ai.agent.AiAgentLog
import com.ahmadkharfan.androidstudiolite.data.ai.llm.LlmChatRequest
import com.ahmadkharfan.androidstudiolite.data.ai.llm.LlmProviderRegistry
import com.ahmadkharfan.androidstudiolite.data.ai.llm.OkHttpLlmClient
import com.ahmadkharfan.androidstudiolite.domain.model.ChatCodeSnippet
import com.ahmadkharfan.androidstudiolite.domain.model.ChatRole
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

data class LlmChatTurn(val role: ChatRole, val text: String)

data class LlmReply(val text: String, val codeSnippet: ChatCodeSnippet? = null)

class AiLlmException(message: String, cause: Throwable? = null) : IOException(message, cause)

class AiLlmGateway internal constructor(
    private val registry: LlmProviderRegistry,
) {
    constructor(httpClient: OkHttpClient = defaultClient()) : this(
        LlmProviderRegistry(OkHttpLlmClient(httpClient)),
    )

    fun testKey(providerId: String, apiKey: String, baseUrl: String? = null) {
        require(apiKey.isNotBlank()) { "API key is empty" }
        registry.require(providerId).test(apiKey, baseUrl)
    }

    fun chat(
        providerId: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        history: List<LlmChatTurn>,
        userMessage: String,
    ): LlmReply {
        val turns = history + LlmChatTurn(ChatRole.USER, userMessage)
        return parseLlmReply(chatRaw(providerId, apiKey, model, systemPrompt, turns))
    }

    fun chatRaw(
        providerId: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        turns: List<LlmChatTurn>,
        baseUrl: String? = null,
    ): String {
        require(apiKey.isNotBlank()) { "API key is empty" }
        val request = chatRequest(providerId, apiKey, model, systemPrompt, turns, baseUrl)
        return registry.require(providerId).chat(request)
    }

    fun chatRawStream(
        providerId: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        turns: List<LlmChatTurn>,
        baseUrl: String? = null,
        onDelta: (String) -> Unit,
    ): String {
        require(apiKey.isNotBlank()) { "API key is empty" }
        val request = chatRequest(providerId, apiKey, model, systemPrompt, turns, baseUrl)
        val accumulated = StringBuilder()
        val emit: (String) -> Unit = { chunk ->
            if (chunk.isNotEmpty()) {
                accumulated.append(chunk)
                onDelta(chunk)
            }
        }
        try {
            registry.require(providerId).stream(request, emit)
        } catch (e: Exception) {
            AiAgentLog.w("Stream", "streaming error provider=$providerId accumulated=${accumulated.length}", e)
            if (accumulated.isEmpty()) {
                val full = registry.require(providerId).chat(request)
                if (full.isNotEmpty()) onDelta(full)
                return full
            }
            if (e is AiLlmException) throw e
        }
        return accumulated.toString().ifBlank { throw AiLlmException("Empty response from $providerId") }
    }

    private fun chatRequest(
        providerId: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        turns: List<LlmChatTurn>,
        baseUrl: String?,
    ) = LlmChatRequest(
        apiKey = apiKey,
        model = model.ifBlank { AiProviderCatalog.defaultModel(providerId) },
        systemPrompt = systemPrompt,
        turns = turns,
        baseUrl = baseUrl,
    )

    fun listModels(providerId: String, apiKey: String, baseUrl: String? = null): List<String> {
        if (apiKey.isBlank()) return emptyList()
        return runCatching {
            registry.find(providerId)?.listModels(apiKey, baseUrl).orEmpty()
        }.getOrDefault(emptyList())
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

fun parseLlmReply(raw: String): LlmReply {
    val fence = Regex("```(\\w*)\\r?\\n([\\s\\S]*?)```")
    val match = fence.find(raw) ?: return LlmReply(text = raw.trim())
    val language = match.groupValues[1].ifBlank { "text" }
    val code = match.groupValues[2].trimEnd()
    val prose = raw.replace(match.value, "").trim()
    return LlmReply(
        text = prose.ifBlank { "Here's a code snippet:" },
        codeSnippet = ChatCodeSnippet(language = language, code = code),
    )
}
