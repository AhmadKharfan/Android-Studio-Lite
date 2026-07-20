package com.ahmadkharfan.androidstudiolite.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import java.net.URI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

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

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val changes: Flow<Unit> = _changes.asSharedFlow()

    override fun credentialsForUrl(url: String): GitCredentials? {
        val host = hostOf(url) ?: return null
        return credentialsForHost(host)
    }

    override fun credentialsForHost(host: String): GitCredentials? {
        val token = prefs.getString(tokenKey(host), null) ?: return null
        val username = prefs.getString(userKey(host), null).orEmpty()
        return GitCredentials(username = username, token = token)
    }

    override fun hasCredentials(host: String): Boolean =
        !prefs.getString(tokenKey(host), null).isNullOrBlank()

    override fun save(host: String, credentials: GitCredentials) {
        prefs.edit()
            .putString(tokenKey(host), credentials.token)
            .putString(userKey(host), credentials.username)
            .apply()
        _changes.tryEmit(Unit)
    }

    override fun clear(host: String) {
        prefs.edit()
            .remove(tokenKey(host))
            .remove(userKey(host))
            .apply()
        _changes.tryEmit(Unit)
    }

    private fun tokenKey(host: String) = "host:$host:token"
    private fun userKey(host: String) = "host:$host:user"

    private companion object {
        const val PREFS_NAME = "git_credentials"

        fun hostOf(url: String): String? = runCatching { URI(url.trim()).host }.getOrNull()
            ?.takeIf { it.isNotBlank() }
    }
}
