package com.ahmadkharfan.androidstudiolite.data.ai

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AiAgentPreferences(
    val enabled: Boolean = true,
    val instructions: String = "",
    val keyStatuses: Map<String, String> = emptyMap(),
    val autoApply: Boolean = false,
    val activeProviderId: String = "",
    val models: Map<String, String> = emptyMap(),
    val customBaseUrl: String = "",
)

class AiAgentPreferencesStore(
    private val dataStore: DataStore<Preferences>,
) {

    fun observe(): Flow<AiAgentPreferences> = dataStore.data.map { prefs ->
        AiAgentPreferences(
            enabled = prefs[ENABLED] ?: true,
            instructions = prefs[INSTRUCTIONS] ?: "",
            keyStatuses = prefs.asMap()
                .filterKeys { it.name.startsWith(STATUS_PREFIX) }
                .mapKeys { it.key.name.removePrefix(STATUS_PREFIX) }
                .mapValues { it.value.toString() },
            autoApply = prefs[AUTO_APPLY] ?: false,
            activeProviderId = prefs[ACTIVE_PROVIDER] ?: "",
            models = prefs.asMap()
                .filterKeys { it.name.startsWith(MODEL_PREFIX) }
                .mapKeys { it.key.name.removePrefix(MODEL_PREFIX) }
                .mapValues { it.value.toString() },
            customBaseUrl = prefs[CUSTOM_BASE_URL] ?: "",
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[ENABLED] = enabled }
    }

    suspend fun setAutoApply(enabled: Boolean) {
        dataStore.edit { it[AUTO_APPLY] = enabled }
    }

    suspend fun setInstructions(instructions: String) {
        dataStore.edit { it[INSTRUCTIONS] = instructions }
    }

    suspend fun setKeyStatus(providerId: String, status: String?) {
        dataStore.edit { prefs ->
            val key = statusKey(providerId)
            if (status == null) prefs.remove(key) else prefs[key] = status
        }
    }

    suspend fun setActiveProvider(providerId: String) {
        dataStore.edit { it[ACTIVE_PROVIDER] = providerId }
    }

    suspend fun setModel(providerId: String, model: String?) {
        dataStore.edit { prefs ->
            val key = modelKey(providerId)
            if (model.isNullOrBlank()) prefs.remove(key) else prefs[key] = model
        }
    }

    suspend fun setCustomBaseUrl(url: String) {
        dataStore.edit { prefs ->
            if (url.isBlank()) prefs.remove(CUSTOM_BASE_URL) else prefs[CUSTOM_BASE_URL] = url
        }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("ai_agent_enabled")
        val AUTO_APPLY = booleanPreferencesKey("ai_agent_auto_apply")
        val INSTRUCTIONS = stringPreferencesKey("ai_agent_instructions")
        val ACTIVE_PROVIDER = stringPreferencesKey("ai_agent_active_provider")
        val CUSTOM_BASE_URL = stringPreferencesKey("ai_custom_base_url")
        const val STATUS_PREFIX = "ai_key_status_"
        const val MODEL_PREFIX = "ai_model_"

        fun statusKey(providerId: String) = stringPreferencesKey("$STATUS_PREFIX$providerId")

        fun modelKey(providerId: String) = stringPreferencesKey("$MODEL_PREFIX$providerId")
    }
}
