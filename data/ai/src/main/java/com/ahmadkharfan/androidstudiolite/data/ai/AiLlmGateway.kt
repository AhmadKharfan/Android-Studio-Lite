package com.ahmadkharfan.androidstudiolite.data.ai

import com.ahmadkharfan.androidstudiolite.data.ai.agent.AiAgentLog
import com.ahmadkharfan.androidstudiolite.domain.model.ChatCodeSnippet
import com.ahmadkharfan.androidstudiolite.domain.model.ChatRole
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class LlmChatTurn(val role: ChatRole, val text: String)

data class LlmReply(val text: String, val codeSnippet: ChatCodeSnippet? = null)

class AiLlmException(message: String, cause: Throwable? = null) : IOException(message, cause)

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
    ): String {
        require(apiKey.isNotBlank()) { "API key is empty" }
        val resolvedModel = model.ifBlank { AiProviderCatalog.defaultModel(providerId) }
        return when (providerId) {
            "anthropic" -> anthropicChat(apiKey, resolvedModel, systemPrompt, turns)
            "gemini" -> geminiChat(apiKey, resolvedModel, systemPrompt, turns)
            "openai", "deepseek", "grok" -> openAiCompatChat(providerId, apiKey, resolvedModel, systemPrompt, turns)
            else -> throw AiLlmException("Unknown provider: $providerId")
        }
    }

    fun chatRawStream(
        providerId: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        turns: List<LlmChatTurn>,
        onDelta: (String) -> Unit,
    ): String {
        require(apiKey.isNotBlank()) { "API key is empty" }
        val resolvedModel = model.ifBlank { AiProviderCatalog.defaultModel(providerId) }
        val accumulated = StringBuilder()
        val emit: (String) -> Unit = { chunk ->
            if (chunk.isNotEmpty()) {
                accumulated.append(chunk)
                onDelta(chunk)
            }
        }
        try {
            when (providerId) {
                "anthropic" -> anthropicStream(apiKey, resolvedModel, systemPrompt, turns, emit)
                "gemini" -> geminiStream(apiKey, resolvedModel, systemPrompt, turns, emit)
                "openai", "deepseek", "grok" ->
                    openAiCompatStream(providerId, apiKey, resolvedModel, systemPrompt, turns, emit)
                else -> throw AiLlmException("Unknown provider: $providerId")
            }
        } catch (e: Exception) {
            AiAgentLog.w("Stream", "streaming error provider=$providerId accumulated=${accumulated.length}", e)

            if (accumulated.isEmpty()) {
                val full = chatRaw(providerId, apiKey, resolvedModel, systemPrompt, turns)
                if (full.isNotEmpty()) onDelta(full)
                return full
            }
            if (e is AiLlmException) throw e
        }
        return accumulated.toString().ifBlank { throw AiLlmException("Empty response from $providerId") }
    }

    fun listModels(providerId: String, apiKey: String): List<String> {
        if (apiKey.isBlank()) return emptyList()
        return runCatching {
            when (providerId) {
                "anthropic" -> anthropicModels(apiKey)
                "gemini" -> geminiModels(apiKey)
                "openai", "deepseek", "grok" -> openAiCompatModels(providerId, apiKey)
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
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
        model: String,
        systemPrompt: String,
        turns: List<LlmChatTurn>,
    ): String {
        val messages = turns.map { turn ->
            when (turn.role) {
                ChatRole.USER -> AnthropicMessage("user", turn.text)
                ChatRole.AI -> AnthropicMessage("assistant", turn.text)
            }
        }
        val body = json.encodeToString(
            AnthropicRequest.serializer(),
            AnthropicRequest(
                model = model,
                maxTokens = 8192,
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
        model: String,
        systemPrompt: String,
        turns: List<LlmChatTurn>,
    ): String {
        val contents = turns.map { turn ->
            val role = if (turn.role == ChatRole.USER) "user" else "model"
            GeminiContent(role, listOf(GeminiPart(turn.text)))
        }
        val body = json.encodeToString(
            GeminiRequest.serializer(),
            GeminiRequest(
                systemInstruction = GeminiSystemInstruction(listOf(GeminiPart(systemPrompt.ifBlank { DEFAULT_SYSTEM }))),
                contents = contents,
            ),
        )
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
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

    private fun anthropicModels(apiKey: String): List<String> {
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/models?limit=100")
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .get()
            .build()
        val body = execute(request)
        return json.decodeFromString(IdListResponse.serializer(), body).data.map { it.id }
    }

    private fun geminiModels(apiKey: String): List<String> {
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey&pageSize=200")
            .get()
            .build()
        val body = execute(request)
        return json.decodeFromString(GeminiModelsResponse.serializer(), body)
            .models
            .map { it.name.removePrefix("models/") }
            .filter { it.startsWith("gemini") }
    }

    private fun openAiCompatModels(providerId: String, apiKey: String): List<String> {
        val request = Request.Builder()
            .url("${openAiBaseUrl(providerId)}/v1/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        val body = execute(request)
        return json.decodeFromString(IdListResponse.serializer(), body).data.map { it.id }
    }

    private fun openAiCompatChat(
        providerId: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        turns: List<LlmChatTurn>,
    ): String {
        val messages = buildList {
            add(OpenAiMessage("system", systemPrompt.ifBlank { DEFAULT_SYSTEM }))
            turns.forEach { turn ->
                val role = if (turn.role == ChatRole.USER) "user" else "assistant"
                add(OpenAiMessage(role, turn.text))
            }
        }
        val body = json.encodeToString(
            OpenAiChatRequest.serializer(),
            OpenAiChatRequest(model = model, messages = messages),
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

    private fun anthropicStream(
        apiKey: String,
        model: String,
        systemPrompt: String,
        turns: List<LlmChatTurn>,
        onDelta: (String) -> Unit,
    ) {
        val messages = turns.map { turn ->
            when (turn.role) {
                ChatRole.USER -> AnthropicMessage("user", turn.text)
                ChatRole.AI -> AnthropicMessage("assistant", turn.text)
            }
        }
        val body = json.encodeToString(
            AnthropicStreamRequest.serializer(),
            AnthropicStreamRequest(
                model = model,
                maxTokens = 8192,
                system = systemPrompt.ifBlank { DEFAULT_SYSTEM },
                messages = messages,
                stream = true,
            ),
        )
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .post(body.toRequestBody(JSON))
            .build()
        readSse(request) { data ->
            val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return@readSse
            if (root["type"]?.jsonPrimitive?.contentOrNull != "content_block_delta") return@readSse
            val text = root["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
            if (!text.isNullOrEmpty()) onDelta(text)
        }
    }

    private fun geminiStream(
        apiKey: String,
        model: String,
        systemPrompt: String,
        turns: List<LlmChatTurn>,
        onDelta: (String) -> Unit,
    ) {
        val contents = turns.map { turn ->
            val role = if (turn.role == ChatRole.USER) "user" else "model"
            GeminiContent(role, listOf(GeminiPart(turn.text)))
        }
        val body = json.encodeToString(
            GeminiRequest.serializer(),
            GeminiRequest(
                systemInstruction = GeminiSystemInstruction(listOf(GeminiPart(systemPrompt.ifBlank { DEFAULT_SYSTEM }))),
                contents = contents,
            ),
        )
        val request = Request.Builder()
            .url(
                "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent" +
                    "?alt=sse&key=$apiKey",
            )
            .post(body.toRequestBody(JSON))
            .build()
        readSse(request) { data ->
            val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return@readSse
            val text = root["candidates"]?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("content")
                ?.jsonObject
                ?.get("parts")
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("text")
                ?.jsonPrimitive
                ?.contentOrNull
            if (!text.isNullOrEmpty()) onDelta(text)
        }
    }

    private fun openAiCompatStream(
        providerId: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        turns: List<LlmChatTurn>,
        onDelta: (String) -> Unit,
    ) {
        val messages = buildList {
            add(OpenAiMessage("system", systemPrompt.ifBlank { DEFAULT_SYSTEM }))
            turns.forEach { turn ->
                val role = if (turn.role == ChatRole.USER) "user" else "assistant"
                add(OpenAiMessage(role, turn.text))
            }
        }
        val body = json.encodeToString(
            OpenAiStreamRequest.serializer(),
            OpenAiStreamRequest(model = model, messages = messages, stream = true),
        )
        val request = Request.Builder()
            .url("${openAiBaseUrl(providerId)}/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toRequestBody(JSON))
            .build()
        readSse(request) { data ->
            if (data == "[DONE]") return@readSse
            val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return@readSse
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

    private fun readSse(request: Request, onData: (String) -> Unit) {
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string().orEmpty()
                throw AiLlmException(parseErrorMessage(err, response.code))
            }
            val source = response.body?.source() ?: throw AiLlmException("Empty stream body")
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data:")) {
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isNotEmpty()) onData(payload)
                }
            }
        }
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

    companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val ANTHROPIC_MODEL = "claude-3-5-haiku-latest"
        const val DEFAULT_SYSTEM =
            "You are an Android development assistant inside Android Studio Lite. " +
                "Respect the project's existing Java or Kotlin language and UI toolkit. " +
                "Be concise. Use fenced code blocks for snippets."

        val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

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

@Serializable
private data class IdListResponse(val data: List<IdEntry> = emptyList())

@Serializable
private data class IdEntry(val id: String)

@Serializable
private data class GeminiModelsResponse(val models: List<GeminiModelInfo> = emptyList())

@Serializable
private data class GeminiModelInfo(val name: String)
