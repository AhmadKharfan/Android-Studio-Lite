package com.ahmadkharfan.androidstudiolite.data.ai.llm

import com.ahmadkharfan.androidstudiolite.data.ai.LlmChatTurn

internal data class LlmChatRequest(
    val apiKey: String,
    val model: String,
    val systemPrompt: String,
    val turns: List<LlmChatTurn>,
    val baseUrl: String?,
)


internal interface LlmProvider {
    fun test(apiKey: String, baseUrl: String?)

    fun chat(request: LlmChatRequest): String

    fun stream(request: LlmChatRequest, onDelta: (String) -> Unit)

    fun listModels(apiKey: String, baseUrl: String?): List<String>
}