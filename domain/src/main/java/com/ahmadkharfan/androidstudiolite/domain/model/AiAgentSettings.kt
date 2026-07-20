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
    /** Human-readable reason the last key test failed (e.g. "HTTP 401: Incorrect API key provided"). */
    val keyError: String? = null,
    /** Selectable models (fetched from the API when available, unioned with a curated fallback). */
    val availableModels: List<String> = emptyList(),
    /** The model id currently chosen for this provider. */
    val selectedModel: String = "",
)

data class AiAgentSettings(
    val enabled: Boolean = true,
    val providers: List<AiProviderConfig> = emptyList(),
    val instructions: String = "",
    /** When true the agent applies file changes without asking; otherwise each change needs approval. */
    val autoApply: Boolean = false,
    /** Default provider used for new chats; blank means "first validated provider". */
    val activeProviderId: String = "",
)
