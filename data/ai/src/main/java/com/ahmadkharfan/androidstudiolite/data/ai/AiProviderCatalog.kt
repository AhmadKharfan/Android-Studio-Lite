package com.ahmadkharfan.androidstudiolite.data.ai

data class AiProviderDefinition(
    val id: String,
    val name: String,
    val icon: String,
    val description: String,
    val featured: Boolean = false,
    val defaultModel: String,
    val curatedModels: List<String>,
)

object AiProviderCatalog {
    val all: List<AiProviderDefinition> = listOf(
        AiProviderDefinition(
            id = "anthropic",
            name = "Anthropic",
            icon = "sparkles",
            description = "Claude · default provider",
            defaultModel = "claude-3-5-sonnet-latest",
            curatedModels = listOf(
                "claude-3-5-sonnet-latest",
                "claude-3-5-haiku-latest",
                "claude-3-opus-latest",
            ),
        ),
        AiProviderDefinition(
            id = "gemini",
            name = "Gemini",
            icon = "gem",
            description = "Google AI Studio key",
            defaultModel = "gemini-2.0-flash",
            curatedModels = listOf(
                "gemini-2.0-flash",
                "gemini-1.5-pro",
                "gemini-1.5-flash",
            ),
        ),
        AiProviderDefinition(
            id = "deepseek",
            name = "DeepSeek",
            icon = "bot",
            description = "OpenAI-compatible API",
            defaultModel = "deepseek-chat",
            curatedModels = listOf("deepseek-chat", "deepseek-reasoner"),
        ),
        AiProviderDefinition(
            id = "openai",
            name = "OpenAI",
            icon = "bot",
            description = "GPT models",
            defaultModel = "gpt-4o-mini",
            curatedModels = listOf("gpt-4o", "gpt-4o-mini", "gpt-4.1", "gpt-4.1-mini", "o4-mini"),
        ),
        AiProviderDefinition(
            id = "grok",
            name = "Grok",
            icon = "bot",
            description = "xAI API",
            defaultModel = "grok-2-latest",
            curatedModels = listOf("grok-2-latest", "grok-beta"),
        ),
    )

    fun byId(id: String): AiProviderDefinition? = all.firstOrNull { it.id == id }

    fun defaultModel(id: String): String = byId(id)?.defaultModel ?: ""
}
