package com.ahmadkharfan.androidstudiolite.data.ai.llm

import com.ahmadkharfan.androidstudiolite.data.ai.AiLlmException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request

internal val llmJson = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

internal val jsonMediaType = "application/json; charset=utf-8".toMediaType()

internal const val DEFAULT_SYSTEM_PROMPT =
    "You are an Android development assistant inside Android Studio Lite. " +
        "Respect the project's existing Java or Kotlin language and UI toolkit. " +
        "Be concise. Use fenced code blocks for snippets."

internal interface LlmHttpClient {
    fun execute(request: Request): String

    fun readSse(request: Request, onData: (String) -> Unit)
}

internal class OkHttpLlmClient(private val httpClient: OkHttpClient) : LlmHttpClient {

    override fun execute(request: Request): String {
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful) return body
            throw AiLlmException(llmErrorMessage(body, response.code))
        }
    }

    override fun readSse(request: Request, onData: (String) -> Unit) {
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val error = response.body?.string().orEmpty()
                throw AiLlmException(llmErrorMessage(error, response.code))
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
}

@Serializable
internal data class IdListResponse(val data: List<IdEntry> = emptyList())

@Serializable
internal data class IdEntry(val id: String)

internal fun llmErrorMessage(body: String, code: Int): String {
    if (body.isBlank()) return "HTTP $code"
    return runCatching {
        val root = llmJson.parseToJsonElement(body).jsonObject
        root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
            ?: root["message"]?.jsonPrimitive?.contentOrNull
            ?: body.take(200)
    }.getOrDefault(body.take(200)).let { "HTTP $code: $it" }
}