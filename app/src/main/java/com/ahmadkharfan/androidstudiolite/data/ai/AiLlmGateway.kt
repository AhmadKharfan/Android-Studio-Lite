package com.ahmadkharfan.androidstudiolite.data.ai

import com.ahmadkharfan.androidstudiolite.domain.model.ChatCodeSnippet
import com.ahmadkharfan.androidstudiolite.domain.model.ChatRole
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class LlmChatTurn(val role: ChatRole, val text: String)

data class LlmReply(val text: String, val codeSnippet: ChatCodeSnippet? = null)

class AiLlmException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Thin HTTP client for the supported LLM providers. Each provider uses its native REST shape; OpenAI-
 * compatible backends (OpenAI, DeepSeek, Grok) share one code path.
 */
class AiLlmGateway(
    private val httpClient: OkHttpClient = defaultClient(),
) {

    fun testKey(providerId: String, apiKey: String) {
        require(apiKey.isNotBlank()) { "API key is empty" }
        when (providerId) {
            "anthropic" -> anthropicTest(apiKey)
            "gemini" -> geminiTest(apiKey)
            "openai", "deepseek", "grok" -> openAiCompatTest(providerId, apiKey)
            else -> throw AiLlmException("Unknown provider: $providerId")
        }
    }

    fun chat(
        providerId: String,
        apiKey: String,
        systemPrompt: String,
        history: List<LlmChatTurn>,
        userMessage: String,
    ): LlmReply {
        require(apiKey.isNotBlank()) { "API key is empty" }
        val raw = when (providerId) {
            "anthropic" -> anthropicChat(apiKey, systemPrompt, history, userMessage)
            "gemini" -> geminiChat(apiKey, systemPrompt, history, userMessage)
            "openai", "deepseek", "grok" -> openAiCompatChat(providerId, apiKey, systemPrompt, history, userMessage)
            else -> throw AiLlmException("Unknown provider: $providerId")
        }
        return parseLlmReply(raw)
    }

    private fun anthropicTest(apiKey: String) {
        val body = json.encodeToString(
            AnthropicRequest.serializer(),
            AnthropicRequest(
                model = ANTHROPIC_MODEL,
                maxTokens = 1,
                system = "Reply with OK.",
                messages = listOf(AnthropicMessage("user", "Hi")),
            ),
        )
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .post(body.toRequestBody(JSON))
            .build()
        execute(request)
    }

    private fun anthropicChat(
        apiKey: String,
        systemPrompt: String,
        history: List<LlmChatTurn>,
        userMessage: String,
    ): String {
        val messages = history.mapNotNull { turn ->
            when (turn.role) {
                ChatRole.USER -> AnthropicMessage("user", turn.text)
                ChatRole.AI -> AnthropicMessage("assistant", turn.text)
            }
        } + AnthropicMessage("user", userMessage)
        val body = json.encodeToString(
            AnthropicRequest.serializer(),
            AnthropicRequest(
                model = ANTHROPIC_MODEL,
                maxTokens = 4096,
                system = systemPrompt.ifBlank { DEFAULT_SYSTEM },
                messages = messages,
            ),
        )
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .post(body.toRequestBody(JSON))
            .build()
        val responseBody = execute(request)
        return json.decodeFromString(AnthropicResponse.serializer(), responseBody)
            .content
            .firstOrNull { it.type == "text" }
            ?.text
            .orEmpty()
            .ifBlank { throw AiLlmException("Empty response from Anthropic") }
    }

    private fun geminiTest(apiKey: String) {
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            .get()
            .build()
        execute(request)
    }

    private fun geminiChat(
        apiKey: String,
        systemPrompt: String,
        history: List<LlmChatTurn>,
        userMessage: String,
    ): String {
        val contents = buildList {
            history.forEach { turn ->
                val role = if (turn.role == ChatRole.USER) "user" else "model"
                add(GeminiContent(role, listOf(GeminiPart(turn.text))))
            }
            add(GeminiContent("user", listOf(GeminiPart(userMessage))))
        }
        val body = json.encodeToString(
            GeminiRequest.serializer(),
            GeminiRequest(
                systemInstruction = GeminiSystemInstruction(listOf(GeminiPart(systemPrompt.ifBlank { DEFAULT_SYSTEM }))),
                contents = contents,
            ),
        )
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent?key=$apiKey")
            .post(body.toRequestBody(JSON))
            .build()
        val responseBody = execute(request)
        val parsed = json.decodeFromString(GeminiResponse.serializer(), responseBody)
        return parsed.candidates
            ?.firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull()
            ?.text
            .orEmpty()
            .ifBlank { throw AiLlmException("Empty response from Gemini") }
    }

    private fun openAiCompatTest(providerId: String, apiKey: String) {
        val request = Request.Builder()
            .url("${openAiBaseUrl(providerId)}/v1/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        execute(request)
    }

    private fun openAiCompatChat(
        providerId: String,
        apiKey: String,
        systemPrompt: String,
        history: List<LlmChatTurn>,
        userMessage: String,
    ): String {
        val messages = buildList {
            add(OpenAiMessage("system", systemPrompt.ifBlank { DEFAULT_SYSTEM }))
            history.forEach { turn ->
                val role = if (turn.role == ChatRole.USER) "user" else "assistant"
                add(OpenAiMessage(role, turn.text))
            }
            add(OpenAiMessage("user", userMessage))
        }
        val body = json.encodeToString(
            OpenAiChatRequest.serializer(),
            OpenAiChatRequest(model = openAiModel(providerId), messages = messages),
        )
        val request = Request.Builder()
            .url("${openAiBaseUrl(providerId)}/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toRequestBody(JSON))
            .build()
        val responseBody = execute(request)
        return json.decodeFromString(OpenAiChatResponse.serializer(), responseBody)
            .choices
            .firstOrNull()
            ?.message
            ?.content
            .orEmpty()
            .ifBlank { throw AiLlmException("Empty response from $providerId") }
    }

    private fun execute(request: Request): String {
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful) return body
            throw AiLlmException(parseErrorMessage(body, response.code))
        }
    }

    private fun parseErrorMessage(body: String, code: Int): String {
        if (body.isBlank()) return "HTTP $code"
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                ?: root["message"]?.jsonPrimitive?.contentOrNull
                ?: body.take(200)
        }.getOrDefault(body.take(200)).let { "HTTP $code: $it" }
    }

    private fun openAiBaseUrl(providerId: String): String = when (providerId) {
        "deepseek" -> "https://api.deepseek.com"
        "grok" -> "https://api.x.ai"
        else -> "https://api.openai.com"
    }

    private fun openAiModel(providerId: String): String = when (providerId) {
        "deepseek" -> "deepseek-chat"
        "grok" -> "grok-2-latest"
        else -> "gpt-4o-mini"
    }

    companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val ANTHROPIC_MODEL = "claude-3-5-haiku-latest"
        const val GEMINI_MODEL = "gemini-2.0-flash"
        const val DEFAULT_SYSTEM =
            "You are an Android development assistant inside Android Studio Lite. " +
                "Prefer Kotlin and Jetpack Compose. Be concise. Use fenced code blocks for snippets."

        val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

/** Pulls an optional fenced code block out of a markdown reply for the chat UI. */
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

@Serializable
private data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String = "",
    val messages: List<AnthropicMessage>,
)

@Serializable
private data class AnthropicMessage(val role: String, val content: String)

@Serializable
private data class AnthropicResponse(val content: List<AnthropicContentBlock>)

@Serializable
private data class AnthropicContentBlock(val type: String, val text: String = "")

@Serializable
private data class GeminiRequest(
    @SerialName("systemInstruction") val systemInstruction: GeminiSystemInstruction? = null,
    val contents: List<GeminiContent>,
)

@Serializable
private data class GeminiSystemInstruction(val parts: List<GeminiPart>)

@Serializable
private data class GeminiContent(val role: String, val parts: List<GeminiPart>)

@Serializable
private data class GeminiPart(val text: String)

@Serializable
private data class GeminiResponse(val candidates: List<GeminiCandidate>? = null)

@Serializable
private data class GeminiCandidate(val content: GeminiContent? = null)

@Serializable
private data class OpenAiChatRequest(val model: String, val messages: List<OpenAiMessage>)

@Serializable
private data class OpenAiMessage(val role: String, val content: String)

@Serializable
private data class OpenAiChatResponse(val choices: List<OpenAiChoice> = emptyList())

@Serializable
private data class OpenAiChoice(val message: OpenAiMessage? = null)
