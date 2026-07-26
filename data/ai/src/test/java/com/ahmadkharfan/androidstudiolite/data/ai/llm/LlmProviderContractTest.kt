package com.ahmadkharfan.androidstudiolite.data.ai.llm

import com.ahmadkharfan.androidstudiolite.data.ai.AiLlmException
import com.ahmadkharfan.androidstudiolite.data.ai.AiLlmGateway
import com.ahmadkharfan.androidstudiolite.data.ai.LlmChatTurn
import com.ahmadkharfan.androidstudiolite.domain.model.ChatRole
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private class ContractHttpClient(
    private val responseBody: String = "",
    private val ssePayloads: List<String> = emptyList(),
) : LlmHttpClient {
    val requests = mutableListOf<Request>()

    override fun execute(request: Request): String {
        requests += request
        return responseBody
    }

    override fun readSse(request: Request, onData: (String) -> Unit) {
        requests += request
        ssePayloads.forEach(onData)
    }
}

private val contractTurns = listOf(
    LlmChatTurn(ChatRole.USER, "Hello"),
    LlmChatTurn(ChatRole.AI, "Hi there"),
    LlmChatTurn(ChatRole.USER, "Continue"),
)

private fun contractGateway(http: LlmHttpClient) = AiLlmGateway(LlmProviderRegistry(http))

private fun Request.bodyJson(): JsonObject {
    val buffer = Buffer()
    requireNotNull(body).writeTo(buffer)
    return Json.parseToJsonElement(buffer.readUtf8()).jsonObject
}

private fun assertRoleAndContent(
    messages: JsonArray,
    index: Int,
    role: String,
    content: String,
) {
    val message = messages[index].jsonObject
    assertEquals(role, message["role"]?.jsonPrimitive?.content)
    assertEquals(content, message["content"]?.jsonPrimitive?.content)
}

private fun assertGeminiContent(
    contents: JsonArray,
    index: Int,
    role: String,
    text: String,
) {
    val content = contents[index].jsonObject
    assertEquals(role, content["role"]?.jsonPrimitive?.content)
    assertEquals(
        text,
        content["parts"]?.jsonArray?.single()?.jsonObject?.get("text")?.jsonPrimitive?.content,
    )
}

private fun assertAnthropicRequest(request: Request, streaming: Boolean) {
    assertEquals("https://api.anthropic.com/v1/messages", request.url.toString())
    assertEquals("POST", request.method)
    assertEquals("anthropic-key", request.header("x-api-key"))
    assertEquals("2023-06-01", request.header("anthropic-version"))
    assertNull(request.header("Authorization"))
    val body = request.bodyJson()
    assertEquals("claude-contract", body["model"]?.jsonPrimitive?.content)
    assertEquals(8192, body["max_tokens"]?.jsonPrimitive?.int)
    assertEquals("System contract", body["system"]?.jsonPrimitive?.content)
    if (streaming) {
        assertTrue(body["stream"]?.jsonPrimitive?.boolean == true)
    } else {
        assertFalse("stream" in body)
    }
    val messages = body["messages"]?.jsonArray ?: error("messages missing")
    assertEquals(3, messages.size)
    assertRoleAndContent(messages, 0, "user", "Hello")
    assertRoleAndContent(messages, 1, "assistant", "Hi there")
    assertRoleAndContent(messages, 2, "user", "Continue")
}

private fun assertOpenAiRequest(request: Request, streaming: Boolean) {
    assertEquals("https://api.openai.com/v1/chat/completions", request.url.toString())
    assertEquals("POST", request.method)
    assertEquals("Bearer openai-key", request.header("Authorization"))
    assertNull(request.header("x-api-key"))
    assertNull(request.header("anthropic-version"))
    val body = request.bodyJson()
    assertEquals("gpt-contract", body["model"]?.jsonPrimitive?.content)
    assertFalse("max_tokens" in body)
    if (streaming) {
        assertTrue(body["stream"]?.jsonPrimitive?.boolean == true)
    } else {
        assertFalse("stream" in body)
    }
    val messages = body["messages"]?.jsonArray ?: error("messages missing")
    assertEquals(4, messages.size)
    assertRoleAndContent(messages, 0, "system", "System contract")
    assertRoleAndContent(messages, 1, "user", "Hello")
    assertRoleAndContent(messages, 2, "assistant", "Hi there")
    assertRoleAndContent(messages, 3, "user", "Continue")
}

private fun assertGeminiRequest(request: Request, streaming: Boolean) {
    val operation = if (streaming) "streamGenerateContent?alt=sse&" else "generateContent?"
    assertEquals(
        "https://generativelanguage.googleapis.com/v1beta/models/" +
            "gemini-contract:$operation" +
            "key=gemini-key",
        request.url.toString(),
    )
    assertEquals("POST", request.method)
    assertNull(request.header("Authorization"))
    assertNull(request.header("x-api-key"))
    assertNull(request.header("anthropic-version"))
    assertEquals("gemini-key", request.url.queryParameter("key"))
    if (streaming) {
        assertEquals("sse", request.url.queryParameter("alt"))
    } else {
        assertNull(request.url.queryParameter("alt"))
    }
    val body = request.bodyJson()
    assertFalse("model" in body)
    assertFalse("max_tokens" in body)
    assertFalse("stream" in body)
    assertEquals(
        "System contract",
        body["systemInstruction"]
            ?.jsonObject
            ?.get("parts")
            ?.jsonArray
            ?.single()
            ?.jsonObject
            ?.get("text")
            ?.jsonPrimitive
            ?.content,
    )
    val contents = body["contents"]?.jsonArray ?: error("contents missing")
    assertEquals(3, contents.size)
    assertGeminiContent(contents, 0, "user", "Hello")
    assertGeminiContent(contents, 1, "model", "Hi there")
    assertGeminiContent(contents, 2, "user", "Continue")
}

class LlmProviderContractTest {

    @Test
    fun anthropicNonStreamingChatUsesMessagesContractAndExtractsTextBlock() {
        val http = ContractHttpClient(
            responseBody = """
                {
                  "id": "msg_01",
                  "type": "message",
                  "content": [
                    {"type": "tool_use", "id": "toolu_01", "name": "inspect", "input": {}},
                    {"type": "text", "text": "Anthropic reply"}
                  ]
                }
            """.trimIndent(),
        )

        val reply = contractGateway(http).chatRaw(
            "anthropic",
            "anthropic-key",
            "claude-contract",
            "System contract",
            contractTurns,
        )

        assertEquals("Anthropic reply", reply)
        assertAnthropicRequest(http.requests.single(), streaming = false)
    }

    @Test
    fun anthropicStreamingChatUsesMessagesStreamFlagAndContentBlockDeltas() {
        val http = ContractHttpClient(
            ssePayloads = listOf(
                """{"type":"message_start","message":{"id":"msg_01"}}""",
                """{"type":"content_block_delta","delta":{"type":"text_delta","text":"Anthropic "}}""",
                """{"type":"content_block_delta","delta":{"type":"text_delta","text":"stream"}}""",
                """{"type":"message_stop"}""",
            ),
        )
        val deltas = mutableListOf<String>()

        val reply = contractGateway(http).chatRawStream(
            "anthropic",
            "anthropic-key",
            "claude-contract",
            "System contract",
            contractTurns,
            onDelta = { deltas += it },
        )

        assertEquals("Anthropic stream", reply)
        assertEquals(listOf("Anthropic ", "stream"), deltas)
        assertAnthropicRequest(http.requests.single(), streaming = true)
    }

    @Test
    fun anthropicWellFormedEmptyResponseNamesVendor() {
        val http = ContractHttpClient(responseBody = """{"content":[]}""")

        val exception = assertThrows(AiLlmException::class.java) {
            contractGateway(http).chatRaw(
                "anthropic",
                "anthropic-key",
                "claude-contract",
                "System contract",
                contractTurns,
            )
        }

        assertEquals("Empty response from Anthropic", exception.message)
    }

    @Test
    fun openAiNonStreamingChatUsesCompletionsContractAndExtractsFirstChoice() {
        val http = ContractHttpClient(
            responseBody = """
                {
                  "id": "chatcmpl-01",
                  "choices": [
                    {
                      "index": 0,
                      "message": {"role": "assistant", "content": "OpenAI reply"}
                    }
                  ]
                }
            """.trimIndent(),
        )

        val reply = contractGateway(http).chatRaw(
            "openai",
            "openai-key",
            "gpt-contract",
            "System contract",
            contractTurns,
        )

        assertEquals("OpenAI reply", reply)
        assertOpenAiRequest(http.requests.single(), streaming = false)
    }

    @Test
    fun openAiStreamingChatUsesCompletionsStreamFlagAndChoiceDeltas() {
        val http = ContractHttpClient(
            ssePayloads = listOf(
                """{"id":"chatcmpl-01","choices":[{"delta":{"role":"assistant"}}]}""",
                """{"id":"chatcmpl-01","choices":[{"delta":{"content":"OpenAI "}}]}""",
                """{"id":"chatcmpl-01","choices":[{"delta":{"content":"stream"}}]}""",
                "[DONE]",
            ),
        )
        val deltas = mutableListOf<String>()

        val reply = contractGateway(http).chatRawStream(
            "openai",
            "openai-key",
            "gpt-contract",
            "System contract",
            contractTurns,
            onDelta = { deltas += it },
        )

        assertEquals("OpenAI stream", reply)
        assertEquals(listOf("OpenAI ", "stream"), deltas)
        assertOpenAiRequest(http.requests.single(), streaming = true)
    }

    @Test
    fun openAiWellFormedEmptyResponseNamesProviderId() {
        val http = ContractHttpClient(responseBody = """{"choices":[]}""")

        val exception = assertThrows(AiLlmException::class.java) {
            contractGateway(http).chatRaw(
                "openai",
                "openai-key",
                "gpt-contract",
                "System contract",
                contractTurns,
            )
        }

        assertEquals("Empty response from openai", exception.message)
    }

    @Test
    fun geminiNonStreamingChatUsesGenerateContentContractAndExtractsFirstPart() {
        val http = ContractHttpClient(
            responseBody = """
                {
                  "candidates": [
                    {
                      "content": {
                        "role": "model",
                        "parts": [{"text": "Gemini reply"}]
                      }
                    }
                  ]
                }
            """.trimIndent(),
        )

        val reply = contractGateway(http).chatRaw(
            "gemini",
            "gemini-key",
            "gemini-contract",
            "System contract",
            contractTurns,
        )

        assertEquals("Gemini reply", reply)
        assertGeminiRequest(http.requests.single(), streaming = false)
    }

    @Test
    fun geminiStreamingChatUsesStreamEndpointAndCandidatePartDeltas() {
        val http = ContractHttpClient(
            ssePayloads = listOf(
                """{"candidates":[{"content":{"role":"model","parts":[{"text":"Gemini "}]}}]}""",
                """{"candidates":[{"content":{"role":"model","parts":[{"text":"stream"}]}}]}""",
            ),
        )
        val deltas = mutableListOf<String>()

        val reply = contractGateway(http).chatRawStream(
            "gemini",
            "gemini-key",
            "gemini-contract",
            "System contract",
            contractTurns,
            onDelta = { deltas += it },
        )

        assertEquals("Gemini stream", reply)
        assertEquals(listOf("Gemini ", "stream"), deltas)
        assertGeminiRequest(http.requests.single(), streaming = true)
    }

    @Test
    fun geminiWellFormedEmptyResponseNamesVendor() {
        val http = ContractHttpClient(responseBody = """{"candidates":[]}""")

        val exception = assertThrows(AiLlmException::class.java) {
            contractGateway(http).chatRaw(
                "gemini",
                "gemini-key",
                "gemini-contract",
                "System contract",
                contractTurns,
            )
        }

        assertEquals("Empty response from Gemini", exception.message)
    }
}
