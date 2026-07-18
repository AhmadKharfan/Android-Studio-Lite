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
    /** Persisted validation outcome per provider id (`valid` / `invalid`). */
    val keyStatuses: Map<String, String> = emptyMap(),
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
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[ENABLED] = enabled }
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

    private companion object {
        val ENABLED = booleanPreferencesKey("ai_agent_enabled")
        val INSTRUCTIONS = stringPreferencesKey("ai_agent_instructions")
        const val STATUS_PREFIX = "ai_key_status_"

        fun statusKey(providerId: String) = stringPreferencesKey("$STATUS_PREFIX$providerId")
    }
}
