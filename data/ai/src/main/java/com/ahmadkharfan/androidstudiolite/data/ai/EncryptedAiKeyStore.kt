package com.ahmadkharfan.androidstudiolite.data.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class EncryptedAiKeyStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: Flow<Unit> = _changes.asSharedFlow()

    fun getKey(providerId: String): String = prefs.getString(keyFor(providerId), null).orEmpty()

    fun setKey(providerId: String, key: String) {
        prefs.edit().putString(keyFor(providerId), key).apply()
        _changes.tryEmit(Unit)
    }

    fun clearKey(providerId: String) {
        prefs.edit().remove(keyFor(providerId)).apply()
        _changes.tryEmit(Unit)
    }

    private fun keyFor(providerId: String) = "provider:$providerId:api_key"

    private companion object {
        const val PREFS_NAME = "ai_api_keys"
    }
}
