package com.ahmadkharfan.androidstudiolite.data.githubactions.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** [TokenVault] backed by encrypted shared preferences, like the Git credential store. */
class EncryptedTokenVault(context: Context) : TokenVault {

    private val prefs: SharedPreferences by lazy {
        val appContext = context.applicationContext
        EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun putAll(values: Map<String, String?>) {
        prefs.edit().apply {
            values.forEach { (key, value) -> if (value == null) remove(key) else putString(key, value) }
        }.apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "github_build_credentials"
    }
}
