package com.ahmadkharfan.androidstudiolite.data.ai.llm

import com.ahmadkharfan.androidstudiolite.data.ai.AiLlmException
import com.ahmadkharfan.androidstudiolite.data.ai.LlmChatTurn
import com.ahmadkharfan.androidstudiolite.domain.model.ChatRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class GeminiProvider(private val http: LlmHttpClient) : LlmProvider {

    override fun test(apiKey: String, baseUrl: String?) {
        val request = Request.Builder()
            .url("$BASE_URL/models?key=$apiKey")
            .get()
            .build()
        http.execute(request)
    }

    override fun chat(request: LlmChatRequest): String {
        val httpRequest = Request.Builder()
            .url("$BASE_URL/models/${request.model}:generateContent?key=${request.apiKey}")
            .post(requestBody(request.systemPrompt, request.turns))
            .build()
        return llmJson.decodeFromString(GeminiResponse.serializer(), http.execute(httpRequest))
            .candidates
            ?.firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull()
            ?.text
            .orEmpty()
            .ifBlank { throw AiLlmException("Empty response from Gemini") }
    }

    override fun stream(request: LlmChatRequest, onDelta: (String) -> Unit) {
        val httpRequest = Request.Builder()
            .url("$BASE_URL/models/${request.model}:streamGenerateContent?alt=sse&key=${request.apiKey}")
            .post(requestBody(request.systemPrompt, request.turns))
            .build()
        http.readSse(httpRequest) { data ->
            val root = runCatching { llmJson.parseToJsonElement(data).jsonObject }.getOrNull() ?: return@readSse
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

    override fun listModels(apiKey: String, baseUrl: String?): List<String> {
        val request = Request.Builder()
            .url("$BASE_URL/models?key=$apiKey&pageSize=200")
            .get()
            .build()
        return llmJson.decodeFromString(GeminiModelsResponse.serializer(), http.execute(request))
            .models
            .map { it.name.removePrefix("models/") }
            .filter { it.startsWith("gemini") }
    }

    private fun requestBody(systemPrompt: String, turns: List<LlmChatTurn>) = llmJson.encodeToString(
        GeminiRequest.serializer(),
        GeminiRequest(
            systemInstruction = GeminiSystemInstruction(
                listOf(GeminiPart(systemPrompt.ifBlank { DEFAULT_SYSTEM_PROMPT })),
            ),
            contents = turns.map { turn ->
                val role = if (turn.role == ChatRole.USER) "user" else "model"
                GeminiContent(role, listOf(GeminiPart(turn.text)))
            },
        ),
    ).toRequestBody(jsonMediaType)

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    }
}

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
private data class GeminiModelsResponse(val models: List<GeminiModelInfo> = emptyList())

@Serializable
private data class GeminiModelInfo(val name: String)