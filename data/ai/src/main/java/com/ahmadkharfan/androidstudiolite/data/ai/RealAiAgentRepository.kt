package com.ahmadkharfan.androidstudiolite.data.ai

import com.ahmadkharfan.androidstudiolite.domain.model.AiAgentSettings
import com.ahmadkharfan.androidstudiolite.domain.model.AiProviderConfig
import com.ahmadkharfan.androidstudiolite.domain.model.ApiKeyStatus
import com.ahmadkharfan.androidstudiolite.domain.repository.AiAgentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

class RealAiAgentRepository(
    private val keyStore: EncryptedAiKeyStore,
    private val preferencesStore: AiAgentPreferencesStore,
    private val gateway: AiLlmGateway,
) : AiAgentRepository {

    private val testingProviders = MutableStateFlow<Set<String>>(emptySet())

    private val keyErrors = MutableStateFlow<Map<String, String>>(emptyMap())

    private val fetchedModels = MutableStateFlow<Map<String, List<String>>>(emptyMap())

    override fun observeSettings(): Flow<AiAgentSettings> = combine(
        preferencesStore.observe(),
        keyStore.changes.onStart { emit(Unit) },
        testingProviders,
        keyErrors,
        fetchedModels,
    ) { prefs, _, testing, errors, fetched ->
        buildSettings(prefs, testing, errors, fetched)
    }

    override suspend fun setEnabled(enabled: Boolean) {
        preferencesStore.setEnabled(enabled)
    }

    override suspend fun setApiKey(providerId: String, key: String) {
        val normalized = key.trim()
        if (normalized.isBlank()) {
            keyStore.clearKey(providerId)
        } else {
            keyStore.setKey(providerId, normalized)
        }
        keyErrors.value = keyErrors.value - providerId
        preferencesStore.setKeyStatus(providerId, null)
    }

    override suspend fun testApiKey(providerId: String) {
        val key = keyStore.getKey(providerId)
        if (key.isBlank()) {
            keyErrors.value = keyErrors.value + (providerId to "Enter an API key first.")
            preferencesStore.setKeyStatus(providerId, "invalid")
            return
        }
        testingProviders.value = testingProviders.value + providerId
        val result = withContext(Dispatchers.IO) {
            runCatching { gateway.testKey(providerId, key) }
        }
        keyErrors.value = if (result.isSuccess) {
            keyErrors.value - providerId
        } else {
            keyErrors.value + (providerId to (result.exceptionOrNull()?.message ?: "Unknown error"))
        }
        preferencesStore.setKeyStatus(providerId, if (result.isSuccess) "valid" else "invalid")
        testingProviders.value = testingProviders.value - providerId
        if (result.isSuccess) refreshModels(providerId)
    }

    override suspend fun setInstructions(instructions: String) {
        preferencesStore.setInstructions(instructions)
    }

    override suspend fun setAutoApply(enabled: Boolean) {
        preferencesStore.setAutoApply(enabled)
    }

    override suspend fun setModel(providerId: String, model: String) {
        preferencesStore.setModel(providerId, model)
    }

    override suspend fun setActiveProvider(providerId: String) {
        preferencesStore.setActiveProvider(providerId)
    }

    override suspend fun refreshModels(providerId: String) {
        val key = keyStore.getKey(providerId)
        if (key.isBlank()) return
        val models = withContext(Dispatchers.IO) { gateway.listModels(providerId, key) }
        if (models.isNotEmpty()) {
            fetchedModels.value = fetchedModels.value + (providerId to models)
        }
    }

    private fun buildSettings(
        prefs: AiAgentPreferences,
        testing: Set<String>,
        errors: Map<String, String>,
        fetched: Map<String, List<String>>,
    ): AiAgentSettings =
        AiAgentSettings(
            enabled = prefs.enabled,
            instructions = prefs.instructions,
            autoApply = prefs.autoApply,
            activeProviderId = prefs.activeProviderId,
            providers = AiProviderCatalog.all.map { def ->
                val apiKey = keyStore.getKey(def.id)
                val status = when {
                    def.id in testing -> ApiKeyStatus.TESTING
                    apiKey.isBlank() -> ApiKeyStatus.EMPTY
                    prefs.keyStatuses[def.id] == "valid" -> ApiKeyStatus.VALID
                    prefs.keyStatuses[def.id] == "invalid" -> ApiKeyStatus.INVALID
                    else -> ApiKeyStatus.EMPTY
                }

                val availableModels = (fetched[def.id].orEmpty() + def.curatedModels).distinct()
                val selectedModel = prefs.models[def.id]?.takeIf { it.isNotBlank() } ?: def.defaultModel
                AiProviderConfig(
                    id = def.id,
                    name = def.name,
                    icon = def.icon,
                    description = def.description,
                    apiKey = apiKey,
                    status = status,
                    featured = def.featured,
                    keyError = errors[def.id]?.takeIf { status == ApiKeyStatus.INVALID },
                    availableModels = availableModels,
                    selectedModel = selectedModel,
                )
            },
        )
}
