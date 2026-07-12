package com.ahmadkharfan.androidstudiolite.domain.model

enum class ApiKeyStatus { EMPTY, TESTING, VALID, INVALID }

data class AiProviderConfig(
    val id: String,
    val name: String,
    val icon: String,
    val description: String,
    val apiKey: String = "",
    val status: ApiKeyStatus = ApiKeyStatus.EMPTY,
    /** Always shown as a full API-key card (e.g. the default provider); others start collapsed. */
    val featured: Boolean = false,
)

data class AiAgentSettings(
    val enabled: Boolean = true,
    val providers: List<AiProviderConfig> = emptyList(),
    val instructions: String = "",
)
