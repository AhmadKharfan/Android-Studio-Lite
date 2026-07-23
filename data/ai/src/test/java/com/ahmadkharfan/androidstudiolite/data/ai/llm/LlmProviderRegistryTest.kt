package com.ahmadkharfan.androidstudiolite.data.ai.llm

import com.ahmadkharfan.androidstudiolite.data.ai.AiLlmException
import com.ahmadkharfan.androidstudiolite.data.ai.AiLlmGateway
import com.ahmadkharfan.androidstudiolite.data.ai.AiProviderCatalog
import com.ahmadkharfan.androidstudiolite.data.ai.LlmChatTurn
import com.ahmadkharfan.androidstudiolite.domain.model.ChatRole
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private class RecordingHttpClient(
    private val onExecute: (Request) -> String = { "" },
    private val onStream: (Request) -> List<String> = { emptyList() },
    private val failStream: Boolean = false,
) : LlmHttpClient {
    val requests = mutableListOf<Request>()

    override fun execute(request: Request): String {
        requests += request
        return onExecute(request)
    }

    override fun readSse(request: Request, onData: (String) -> Unit) {
        requests += request
        if (failStream) throw AiLlmException("stream boom")
        onStream(request).forEach(onData)
    }

    val lastUrl: String get() = requests.last().url.toString()
}

private fun gatewayWith(http: LlmHttpClient) = AiLlmGateway(LlmProviderRegistry(http))

private val turns = listOf(LlmChatTurn(ChatRole.USER, "hi"))

class LlmProviderRegistryTest {

    @Test
    fun chatRoutesAnthropicToItsMessagesEndpoint() {
        val http = RecordingHttpClient(onExecute = { """{"content":[{"type":"text","text":"pong"}]}""" })
        val reply = gatewayWith(http).chatRaw("anthropic", "key", "claude-x", "", turns)

        assertEquals("pong", reply)
        assertEquals("https://api.anthropic.com/v1/messages", http.lastUrl)
        assertEquals("key", http.requests.last().header("x-api-key"))
    }

    @Test
    fun chatRoutesOpenAiCompatByProviderId() {
        val http = RecordingHttpClient(onExecute = { """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}""" })
        gatewayWith(http).chatRaw("deepseek", "key", "deepseek-chat", "", turns)

        assertEquals("https://api.deepseek.com/v1/chat/completions", http.lastUrl)
        assertEquals("Bearer key", http.requests.last().header("Authorization"))
    }

    @Test
    fun blankModelFallsBackToProviderDefault() {
        val http = RecordingHttpClient(onExecute = { """{"candidates":[{"content":{"role":"model","parts":[{"text":"x"}]}}]}""" })
        gatewayWith(http).chatRaw("gemini", "key", model = "", systemPrompt = "", turns = turns)

        val defaultModel = AiProviderCatalog.defaultModel("gemini")
        assertTrue(defaultModel.isNotBlank())
        assertTrue(http.lastUrl.contains("/models/$defaultModel:generateContent"))
    }

    @Test
    fun unknownProviderThrows() {
        val gateway = gatewayWith(RecordingHttpClient())
        assertThrows(AiLlmException::class.java) {
            gateway.chatRaw("nope", "key", "m", "", turns)
        }
    }

    @Test
    fun blankApiKeyIsRejectedBeforeDispatch() {
        val http = RecordingHttpClient()
        assertThrows(IllegalArgumentException::class.java) {
            gatewayWith(http).chatRaw("anthropic", "", "m", "", turns)
        }
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun streamFallsBackToNonStreamWhenNothingArrived() {
        val http = RecordingHttpClient(
            onExecute = { """{"content":[{"type":"text","text":"recovered"}]}""" },
            failStream = true,
        )
        val deltas = mutableListOf<String>()
        val result = gatewayWith(http).chatRawStream("anthropic", "key", "m", "", turns) { deltas += it }

        assertEquals("recovered", result)
        assertEquals(listOf("recovered"), deltas)
    }

    @Test
    fun streamAccumulatesDeltas() {
        val http = RecordingHttpClient(
            onStream = {
                listOf(
                    """{"type":"content_block_delta","delta":{"text":"Hel"}}""",
                    """{"type":"content_block_delta","delta":{"text":"lo"}}""",
                )
            },
        )
        val deltas = mutableListOf<String>()
        val result = gatewayWith(http).chatRawStream("anthropic", "key", "m", "", turns) { deltas += it }

        assertEquals("Hello", result)
        assertEquals(listOf("Hel", "lo"), deltas)
    }

    @Test
    fun listModelsReturnsEmptyForBlankKeyOrUnknownProvider() {
        val gateway = gatewayWith(RecordingHttpClient(onExecute = { """{"data":[{"id":"m"}]}""" }))
        assertEquals(emptyList<String>(), gateway.listModels("anthropic", ""))
        assertEquals(emptyList<String>(), gateway.listModels("nope", "key"))
    }
}

class OpenAiCompatBaseUrlTest {

    @Test
    fun resolvesKnownHosts() {
        assertEquals("https://api.openai.com", openAiCompatBaseUrl("openai", null))
        assertEquals("https://api.deepseek.com", openAiCompatBaseUrl("deepseek", null))
        assertEquals("https://api.x.ai", openAiCompatBaseUrl("grok", null))
    }

    @Test
    fun customTrimsTrailingSlash() {
        assertEquals(
            "https://host.example/api",
            openAiCompatBaseUrl(AiProviderCatalog.CUSTOM_ID, " https://host.example/api/ "),
        )
    }

    @Test
    fun customWithoutBaseUrlThrows() {
        assertThrows(AiLlmException::class.java) {
            openAiCompatBaseUrl(AiProviderCatalog.CUSTOM_ID, "  ")
        }
    }
}

class LlmErrorMessageTest {

    @Test
    fun extractsNestedErrorMessage() {
        assertEquals("HTTP 400: bad key", llmErrorMessage("""{"error":{"message":"bad key"}}""", 400))
    }

    @Test
    fun fallsBackToTopLevelMessageThenRawThenCode() {
        assertEquals("HTTP 429: slow down", llmErrorMessage("""{"message":"slow down"}""", 429))
        assertEquals("HTTP 500: not json", llmErrorMessage("not json", 500))
        assertEquals("HTTP 503", llmErrorMessage("", 503))
    }
}