package com.ahmadkharfan.androidstudiolite.data.remote

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ahmadkharfan.androidstudiolite.data.build.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.buildServerDataStore by preferencesDataStore(name = "build_server")

private val KEY_BASE_URL = stringPreferencesKey("base_url")
private val KEY_DEVICE_TOKEN = stringPreferencesKey("device_token")

class ServerSettingsRepository(private val context: Context) {

    fun observe(): Flow<ServerSettings> =
        context.buildServerDataStore.data.map { prefs ->
            ServerSettings(
                baseUrl = (prefs[KEY_BASE_URL] ?: BuildConfig.DEFAULT_BUILD_SERVER_URL).trimEnd('/'),
                deviceToken = prefs[KEY_DEVICE_TOKEN],
            )
        }

    suspend fun current(): ServerSettings = observe().first()

    suspend fun setBaseUrl(url: String) {
        context.buildServerDataStore.edit { it[KEY_BASE_URL] = url.trim().trimEnd('/') }
    }

    suspend fun setDeviceToken(token: String) {
        context.buildServerDataStore.edit { it[KEY_DEVICE_TOKEN] = token }
    }

    suspend fun clearDeviceToken() {
        context.buildServerDataStore.edit { it.remove(KEY_DEVICE_TOKEN) }
    }
}
