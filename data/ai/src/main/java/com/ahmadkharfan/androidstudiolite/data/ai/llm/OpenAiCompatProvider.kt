package com.ahmadkharfan.androidstudiolite.data.ai.llm

import com.ahmadkharfan.androidstudiolite.data.ai.AiLlmException
import com.ahmadkharfan.androidstudiolite.data.ai.AiProviderCatalog
import com.ahmadkharfan.androidstudiolite.data.ai.LlmChatTurn
import com.ahmadkharfan.androidstudiolite.domain.model.ChatRole
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class OpenAiCompatProvider(
    private val providerId: String,
    private val http: LlmHttpClient,
) : LlmProvider {

    override fun test(apiKey: String, baseUrl: String?) {
        http.execute(getRequest("${resolveBaseUrl(baseUrl)}/v1/models", apiKey))
    }

    override fun chat(request: LlmChatRequest): String {
        val body = llmJson.encodeToString(
            OpenAiChatRequest.serializer(),
            OpenAiChatRequest(model = request.model, messages = messages(request.systemPrompt, request.turns)),
        )
        val httpRequest = completionsRequest(request.apiKey, request.baseUrl, body)
        return llmJson.decodeFromString(OpenAiChatResponse.serializer(), http.execute(httpRequest))
            .choices
            .firstOrNull()
            ?.message
            ?.content
            .orEmpty()
            .ifBlank { throw AiLlmException("Empty response from $providerId") }
    }

    override fun stream(request: LlmChatRequest, onDelta: (String) -> Unit) {
        val body = llmJson.encodeToString(
            OpenAiStreamRequest.serializer(),
            OpenAiStreamRequest(
                model = request.model,
                messages = messages(request.systemPrompt, request.turns),
                stream = true,
            ),
        )
        http.readSse(completionsRequest(request.apiKey, request.baseUrl, body)) { data ->
            if (data == "[DONE]") return@readSse
            val root = runCatching { llmJson.parseToJsonElement(data).jsonObject }.getOrNull() ?: return@readSse
            val text = root["choices"]?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("delta")
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.contentOrNull
            if (!text.isNullOrEmpty()) onDelta(text)
        }
    }

    override fun listModels(apiKey: String, baseUrl: String?): List<String> {
        val request = getRequest("${resolveBaseUrl(baseUrl)}/v1/models", apiKey)
        return llmJson.decodeFromString(IdListResponse.serializer(), http.execute(request)).data.map { it.id }
    }

    private fun resolveBaseUrl(customBaseUrl: String?): String = openAiCompatBaseUrl(providerId, customBaseUrl)

    private fun completionsRequest(apiKey: String, baseUrl: String?, jsonBody: String): Request =
        Request.Builder()
            .url("${resolveBaseUrl(baseUrl)}/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(jsonBody.toRequestBody(jsonMediaType))
            .build()

    private fun getRequest(url: String, apiKey: String): Request = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $apiKey")
        .get()
        .build()

    private fun messages(systemPrompt: String, turns: List<LlmChatTurn>): List<OpenAiMessage> = buildList {
        add(OpenAiMessage("system", systemPrompt.ifBlank { DEFAULT_SYSTEM_PROMPT }))
        turns.forEach { turn ->
            val role = if (turn.role == ChatRole.USER) "user" else "assistant"
            add(OpenAiMessage(role, turn.text))
        }
    }
}

/** Resolves the API host for an OpenAI-compatible [providerId], honoring a [customBaseUrl] for custom providers. */
internal fun openAiCompatBaseUrl(providerId: String, customBaseUrl: String?): String = when (providerId) {
    "deepseek" -> "https://api.deepseek.com"
    "grok" -> "https://api.x.ai"
    AiProviderCatalog.CUSTOM_ID -> {
        val normalized = customBaseUrl?.trim()?.trimEnd('/').orEmpty()
        if (normalized.isBlank()) throw AiLlmException("Set a base URL for the custom provider")
        normalized
    }
    else -> "https://api.openai.com"
}

@Serializable
private data class OpenAiChatRequest(val model: String, val messages: List<OpenAiMessage>)

@Serializable
private data class OpenAiStreamRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val stream: Boolean = true,
)

@Serializable
private data class OpenAiMessage(val role: String, val content: String)

@Serializable
private data class OpenAiChatResponse(val choices: List<OpenAiChoice> = emptyList())

@Serializable
private data class OpenAiChoice(val message: OpenAiMessage? = null)