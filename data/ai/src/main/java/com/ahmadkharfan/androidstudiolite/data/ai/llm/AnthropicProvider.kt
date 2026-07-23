package com.ahmadkharfan.androidstudiolite.data.ai.llm

import com.ahmadkharfan.androidstudiolite.data.ai.AiLlmException
import com.ahmadkharfan.androidstudiolite.data.ai.LlmChatTurn
import com.ahmadkharfan.androidstudiolite.domain.model.ChatRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class AnthropicProvider(private val http: LlmHttpClient) : LlmProvider {

    override fun test(apiKey: String, baseUrl: String?) {
        val body = llmJson.encodeToString(
            AnthropicRequest.serializer(),
            AnthropicRequest(
                model = TEST_MODEL,
                maxTokens = 1,
                system = "Reply with OK.",
                messages = listOf(AnthropicMessage("user", "Hi")),
            ),
        )
        http.execute(messagesRequest(apiKey, body))
    }

    override fun chat(request: LlmChatRequest): String {
        val body = llmJson.encodeToString(
            AnthropicRequest.serializer(),
            AnthropicRequest(
                model = request.model,
                maxTokens = MAX_TOKENS,
                system = request.systemPrompt.ifBlank { DEFAULT_SYSTEM_PROMPT },
                messages = request.turns.toAnthropicMessages(),
            ),
        )
        val httpRequest = messagesRequest(request.apiKey, body)
        return llmJson.decodeFromString(AnthropicResponse.serializer(), http.execute(httpRequest))
            .content
            .firstOrNull { it.type == "text" }
            ?.text
            .orEmpty()
            .ifBlank { throw AiLlmException("Empty response from Anthropic") }
    }

    override fun stream(request: LlmChatRequest, onDelta: (String) -> Unit) {
        val body = llmJson.encodeToString(
            AnthropicStreamRequest.serializer(),
            AnthropicStreamRequest(
                model = request.model,
                maxTokens = MAX_TOKENS,
                system = request.systemPrompt.ifBlank { DEFAULT_SYSTEM_PROMPT },
                messages = request.turns.toAnthropicMessages(),
                stream = true,
            ),
        )
        val httpRequest = messagesRequest(request.apiKey, body)
        http.readSse(httpRequest) { data ->
            val root = runCatching { llmJson.parseToJsonElement(data).jsonObject }.getOrNull() ?: return@readSse
            if (root["type"]?.jsonPrimitive?.contentOrNull != "content_block_delta") return@readSse
            val text = root["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
            if (!text.isNullOrEmpty()) onDelta(text)
        }
    }

    override fun listModels(apiKey: String, baseUrl: String?): List<String> {
        val request = Request.Builder()
            .url("$BASE_URL/v1/models?limit=100")
            .header("x-api-key", apiKey)
            .header("anthropic-version", VERSION)
            .get()
            .build()
        return llmJson.decodeFromString(IdListResponse.serializer(), http.execute(request)).data.map { it.id }
    }

    private fun messagesRequest(apiKey: String, jsonBody: String): Request = Request.Builder()
        .url("$BASE_URL/v1/messages")
        .header("x-api-key", apiKey)
        .header("anthropic-version", VERSION)
        .post(jsonBody.toRequestBody(jsonMediaType))
        .build()

    private fun List<LlmChatTurn>.toAnthropicMessages(): List<AnthropicMessage> = map { turn ->
        when (turn.role) {
            ChatRole.USER -> AnthropicMessage("user", turn.text)
            ChatRole.AI -> AnthropicMessage("assistant", turn.text)
        }
    }

    private companion object {
        const val BASE_URL = "https://api.anthropic.com"
        const val VERSION = "2023-06-01"
        const val TEST_MODEL = "claude-3-5-haiku-latest"
        const val MAX_TOKENS = 8192
    }
}

@Serializable
private data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String = "",
    val messages: List<AnthropicMessage>,
)

@Serializable
private data class AnthropicStreamRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String = "",
    val messages: List<AnthropicMessage>,
    val stream: Boolean = true,
)

@Serializable
private data class AnthropicMessage(val role: String, val content: String)

@Serializable
private data class AnthropicResponse(val content: List<AnthropicContentBlock>)

@Serializable
private data class AnthropicContentBlock(val type: String, val text: String = "")