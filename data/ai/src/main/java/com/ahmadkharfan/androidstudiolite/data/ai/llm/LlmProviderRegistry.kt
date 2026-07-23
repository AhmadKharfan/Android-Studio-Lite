package com.ahmadkharfan.androidstudiolite.data.ai.llm

import com.ahmadkharfan.androidstudiolite.data.ai.AiLlmException
import com.ahmadkharfan.androidstudiolite.data.ai.AiProviderCatalog

internal class LlmProviderRegistry(http: LlmHttpClient) {

    private val providers: Map<String, LlmProvider> = buildMap {
        put("anthropic", AnthropicProvider(http))
        put("gemini", GeminiProvider(http))
        for (id in listOf("openai", "deepseek", "grok", AiProviderCatalog.CUSTOM_ID)) {
            put(id, OpenAiCompatProvider(id, http))
        }
    }

    fun require(providerId: String): LlmProvider =
        providers[providerId] ?: throw AiLlmException("Unknown provider: $providerId")

    fun find(providerId: String): LlmProvider? = providers[providerId]
}