package com.ahmadkharfan.androidstudiolite.domain.model

enum class ApiKeyStatus { EMPTY, TESTING, VALID, INVALID }

data class AiProviderConfig(
    val id: String,
    val name: String,
    val icon: String,
    val description: String,
    val apiKey: String = "",
    val status: ApiKeyStatus = ApiKeyStatus.EMPTY,
    val featured: Boolean = false,
    val keyError: String? = null,
    val availableModels: List<String> = emptyList(),
    val selectedModel: String = "",
    val baseUrl: String = "",
    val requiresBaseUrl: Boolean = false,
)

data class AiAgentSettings(
    val enabled: Boolean = true,
    val providers: List<AiProviderConfig> = emptyList(),
    val instructions: String = "",
    val autoApply: Boolean = false,
    val activeProviderId: String = "",
)
