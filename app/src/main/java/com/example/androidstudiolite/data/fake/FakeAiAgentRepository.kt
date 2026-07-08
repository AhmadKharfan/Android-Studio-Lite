package com.example.androidstudiolite.data.fake

import com.example.androidstudiolite.domain.model.AiAgentSettings
import com.example.androidstudiolite.domain.model.AiProviderConfig
import com.example.androidstudiolite.domain.model.ApiKeyStatus
import com.example.androidstudiolite.domain.repository.AiAgentRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeAiAgentRepository : AiAgentRepository {

    private val settings = MutableStateFlow(
        AiAgentSettings(
            enabled = true,
            providers = listOf(
                AiProviderConfig(
                    id = "anthropic",
                    name = "Anthropic",
                    icon = "sparkles",
                    description = "Claude · default provider",
                    apiKey = "sk-ant-9f2kx01m",
                    status = ApiKeyStatus.VALID,
                    featured = true,
                ),
                AiProviderConfig(id = "gemini", name = "Gemini", icon = "gem", description = "Google AI Studio key", featured = true),
                AiProviderConfig(id = "deepseek", name = "DeepSeek", icon = "bot", description = "Not configured"),
                AiProviderConfig(id = "openai", name = "OpenAI", icon = "bot", description = "Not configured"),
                AiProviderConfig(id = "grok", name = "Grok", icon = "bot", description = "Not configured"),
            ),
            instructions = "Prefer Kotlin idioms. Keep functions under 30 lines. Never add dependencies without asking.",
        ),
    )

    override fun observeSettings(): StateFlow<AiAgentSettings> = settings

    override suspend fun setEnabled(enabled: Boolean) {
        settings.value = settings.value.copy(enabled = enabled)
    }

    override suspend fun setApiKey(providerId: String, key: String) {
        updateProvider(providerId) { it.copy(apiKey = key, status = if (key.isBlank()) ApiKeyStatus.EMPTY else it.status) }
    }

    override suspend fun testApiKey(providerId: String) {
        updateProvider(providerId) { it.copy(status = ApiKeyStatus.TESTING) }
        delay(700)
        updateProvider(providerId) { it.copy(status = if (it.apiKey.isNotBlank()) ApiKeyStatus.VALID else ApiKeyStatus.INVALID) }
    }

    override suspend fun setInstructions(instructions: String) {
        settings.value = settings.value.copy(instructions = instructions)
    }

    private fun updateProvider(providerId: String, transform: (AiProviderConfig) -> AiProviderConfig) {
        settings.value = settings.value.copy(
            providers = settings.value.providers.map { if (it.id == providerId) transform(it) else it },
        )
    }
}
