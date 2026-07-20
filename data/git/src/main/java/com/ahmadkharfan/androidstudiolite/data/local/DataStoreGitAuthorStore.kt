package com.ahmadkharfan.androidstudiolite.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ahmadkharfan.androidstudiolite.domain.model.GitAuthorConfig
import com.ahmadkharfan.androidstudiolite.domain.repository.GitAuthorStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class DataStoreGitAuthorStore(
    private val dataStore: DataStore<Preferences>,
) : GitAuthorStore {
    override fun observe(): Flow<GitAuthorConfig?> = dataStore.data.map(::read)

    override suspend fun get(): GitAuthorConfig? = read(dataStore.data.first())

    override suspend fun set(config: GitAuthorConfig?) {
        dataStore.edit { prefs ->
            if (config == null) {
                prefs.remove(NAME)
                prefs.remove(EMAIL)
            } else {
                prefs[NAME] = config.name.trim()
                prefs[EMAIL] = config.email.trim()
            }
        }
    }

    private fun read(preferences: Preferences): GitAuthorConfig? {
        val name = preferences[NAME]?.takeIf { it.isNotBlank() } ?: return null
        val email = preferences[EMAIL]?.takeIf { it.isNotBlank() } ?: return null
        return GitAuthorConfig(name, email)
    }

    private companion object {
        val NAME = stringPreferencesKey("git_author_name")
        val EMAIL = stringPreferencesKey("git_author_email")
    }
}
