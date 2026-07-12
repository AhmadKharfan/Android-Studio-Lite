package com.ahmadkharfan.androidstudiolite.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import java.net.URI

/**
 * [GitCredentialStore] backed by [EncryptedSharedPreferences] (AES-256 over a Keystore-held master
 * key). Personal access tokens never touch plaintext prefs or disk-readable storage.
 */
class EncryptedGitCredentialStore(context: Context) : GitCredentialStore {

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

    override fun credentialsForUrl(url: String): GitCredentials? {
        val host = hostOf(url) ?: return null
        val token = prefs.getString(tokenKey(host), null) ?: return null
        val username = prefs.getString(userKey(host), null).orEmpty()
        return GitCredentials(username = username, token = token)
    }

    override fun save(host: String, credentials: GitCredentials) {
        prefs.edit()
            .putString(tokenKey(host), credentials.token)
            .putString(userKey(host), credentials.username)
            .apply()
    }

    override fun clear(host: String) {
        prefs.edit()
            .remove(tokenKey(host))
            .remove(userKey(host))
            .apply()
    }

    private fun tokenKey(host: String) = "host:$host:token"
    private fun userKey(host: String) = "host:$host:user"

    private companion object {
        const val PREFS_NAME = "git_credentials"

        fun hostOf(url: String): String? = runCatching { URI(url.trim()).host }.getOrNull()
            ?.takeIf { it.isNotBlank() }
    }
}
