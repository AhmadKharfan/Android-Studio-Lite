package com.ahmadkharfan.androidstudiolite.data.ai

data class AiProviderDefinition(
    val id: String,
    val name: String,
    val icon: String,
    val description: String,
    val featured: Boolean = false,
)

object AiProviderCatalog {
    val all: List<AiProviderDefinition> = listOf(
        AiProviderDefinition(
            id = "anthropic",
            name = "Anthropic",
            icon = "sparkles",
            description = "Claude · default provider",
            featured = true,
        ),
        AiProviderDefinition(
            id = "gemini",
            name = "Gemini",
            icon = "gem",
            description = "Google AI Studio key",
            featured = true,
        ),
        AiProviderDefinition(id = "deepseek", name = "DeepSeek", icon = "bot", description = "OpenAI-compatible API"),
        AiProviderDefinition(id = "openai", name = "OpenAI", icon = "bot", description = "GPT models"),
        AiProviderDefinition(id = "grok", name = "Grok", icon = "bot", description = "xAI API"),
    )

    fun byId(id: String): AiProviderDefinition? = all.firstOrNull { it.id == id }
}
