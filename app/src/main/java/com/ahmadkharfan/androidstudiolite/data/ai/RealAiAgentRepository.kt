package com.ahmadkharfan.androidstudiolite.data.ai

import com.ahmadkharfan.androidstudiolite.domain.model.AiAgentSettings
import com.ahmadkharfan.androidstudiolite.domain.model.AiProviderConfig
import com.ahmadkharfan.androidstudiolite.domain.model.ApiKeyStatus
import com.ahmadkharfan.androidstudiolite.domain.repository.AiAgentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart

class RealAiAgentRepository(
    private val keyStore: EncryptedAiKeyStore,
    private val preferencesStore: AiAgentPreferencesStore,
    private val gateway: AiLlmGateway,
) : AiAgentRepository {

    private val testingProviders = MutableStateFlow<Set<String>>(emptySet())

    override fun observeSettings(): Flow<AiAgentSettings> = combine(
        preferencesStore.observe(),
        keyStore.changes.onStart { emit(Unit) },
        testingProviders,
    ) { prefs, _, testing ->
        buildSettings(prefs, testing)
    }

    override suspend fun setEnabled(enabled: Boolean) {
        preferencesStore.setEnabled(enabled)
    }

    override suspend fun setApiKey(providerId: String, key: String) {
        if (key.isBlank()) {
            keyStore.clearKey(providerId)
        } else {
            keyStore.setKey(providerId, key.trim())
        }
        preferencesStore.setKeyStatus(providerId, null)
    }

    override suspend fun testApiKey(providerId: String) {
        val key = keyStore.getKey(providerId)
        testingProviders.value = testingProviders.value + providerId
        val status = runCatching { gateway.testKey(providerId, key) }
            .fold(
                onSuccess = { "valid" },
                onFailure = { "invalid" },
            )
        preferencesStore.setKeyStatus(providerId, status)
        testingProviders.value = testingProviders.value - providerId
    }

    override suspend fun setInstructions(instructions: String) {
        preferencesStore.setInstructions(instructions)
    }

    private fun buildSettings(prefs: AiAgentPreferences, testing: Set<String>): AiAgentSettings =
        AiAgentSettings(
            enabled = prefs.enabled,
            instructions = prefs.instructions,
            providers = AiProviderCatalog.all.map { def ->
                val apiKey = keyStore.getKey(def.id)
                val status = when {
                    def.id in testing -> ApiKeyStatus.TESTING
                    apiKey.isBlank() -> ApiKeyStatus.EMPTY
                    prefs.keyStatuses[def.id] == "valid" -> ApiKeyStatus.VALID
                    prefs.keyStatuses[def.id] == "invalid" -> ApiKeyStatus.INVALID
                    else -> ApiKeyStatus.EMPTY
                }
                AiProviderConfig(
                    id = def.id,
                    name = def.name,
                    icon = def.icon,
                    description = def.description,
                    apiKey = apiKey,
                    status = status,
                    featured = def.featured,
                )
            },
        )
}
