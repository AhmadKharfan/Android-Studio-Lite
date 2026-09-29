package com.ahmadkharfan.androidstudiolite.data.githubactions.auth

import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.model.GitHubTokenGrant
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Encrypted key-value storage for the build token. */
interface TokenVault {
    fun get(key: String): String?
    fun putAll(values: Map<String, String?>)
    fun clear()
}

/**
 * The GitHub credential used for GitHub Actions builds, kept apart from the Git features' sign-in so builds
 * can run on a GitHub App token limited to the build repository.
 *
 * A GitHub App's user token expires (8 hours by default) and comes with a refresh token (6 months).
 * [accessToken] renews it shortly before it expires. Tokens from device flow refresh without a client
 * secret, so none is shipped. A personal access token saved through [save] is kept as is and never refreshed.
 *
 * Implements [GitCredentialStore] for github.com only, so the existing sign-in dialog can manage it.
 */
class GitHubBuildCredentials(
    private val vault: TokenVault,
    private val refresher: GitHubTokenRefresher,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : GitCredentialStore {

    private val refreshLock = Mutex()
    private val changeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val changes: Flow<Unit> = changeEvents.asSharedFlow()

    /** A token that is valid now, renewing it first when it is about to expire; null when signed out. */
    suspend fun accessToken(): String? {
        val current = vault.get(ACCESS_TOKEN) ?: return null
        if (!expiresSoon()) return current
        return refreshLock.withLock {
            // Another caller may have refreshed while this one waited.
            val latest = vault.get(ACCESS_TOKEN) ?: return@withLock null
            if (!expiresSoon()) return@withLock latest
            refresh(latest)
        }
    }

    /** Stores a grant from device-flow sign-in. */
    fun saveGrant(grant: GitHubTokenGrant) {
        vault.putAll(entriesFor(grant))
        changeEvents.tryEmit(Unit)
    }

    override fun credentialsForUrl(url: String): GitCredentials? =
        if (hostOf(url) == GITHUB_HOST) credentialsForHost(GITHUB_HOST) else null

    override fun credentialsForHost(host: String): GitCredentials? =
        vault.get(ACCESS_TOKEN)?.takeIf { host == GITHUB_HOST }?.let { GitCredentials(TOKEN_USERNAME, it) }

    override fun hasCredentials(host: String): Boolean = host == GITHUB_HOST && vault.get(ACCESS_TOKEN) != null

    /** Saves a token entered by hand (a personal access token); it has no expiry to manage. */
    override fun save(host: String, credentials: GitCredentials) {
        if (host != GITHUB_HOST) return
        saveGrant(GitHubTokenGrant(accessToken = credentials.token))
    }

    override fun clear(host: String) {
        if (host != GITHUB_HOST) return
        vault.clear()
        changeEvents.tryEmit(Unit)
    }

    private fun expiresSoon(): Boolean {
        val expiresAt = vault.get(EXPIRES_AT)?.toLongOrNull() ?: return false
        return nowMillis() >= expiresAt - REFRESH_MARGIN_MS
    }

    private suspend fun refresh(current: String): String? {
        val refreshToken = vault.get(REFRESH_TOKEN)
        val refreshExpired = vault.get(REFRESH_EXPIRES_AT)?.toLongOrNull()?.let { nowMillis() >= it } ?: false
        if (refreshToken == null || refreshExpired) return signedOut()
        return when (val result = refresher.refresh(refreshToken)) {
            is GitHubTokenRefresher.Result.Renewed -> {
                saveGrant(result.grant)
                result.grant.accessToken
            }
            // GitHub no longer accepts the refresh token: only a new sign-in helps.
            GitHubTokenRefresher.Result.Rejected -> signedOut()
            // A network problem: keep the tokens and let the next call try again.
            GitHubTokenRefresher.Result.Unavailable -> current
        }
    }

    private fun signedOut(): String? {
        clear(GITHUB_HOST)
        return null
    }

    private fun entriesFor(grant: GitHubTokenGrant): Map<String, String?> {
        val now = nowMillis()
        return mapOf(
            ACCESS_TOKEN to grant.accessToken,
            EXPIRES_AT to grant.expiresInSeconds?.let { (now + it * 1000).toString() },
            REFRESH_TOKEN to grant.refreshToken,
            REFRESH_EXPIRES_AT to grant.refreshTokenExpiresInSeconds?.let { (now + it * 1000).toString() },
        )
    }

    private fun hostOf(url: String): String? =
        runCatching { java.net.URI(url).host?.lowercase() }.getOrNull()

    companion object {
        const val GITHUB_HOST = "github.com"
        private const val TOKEN_USERNAME = "x-access-token"
        private const val ACCESS_TOKEN = "access_token"
        private const val EXPIRES_AT = "access_token_expires_at"
        private const val REFRESH_TOKEN = "refresh_token"
        private const val REFRESH_EXPIRES_AT = "refresh_token_expires_at"
        private const val REFRESH_MARGIN_MS = 5 * 60 * 1000L
    }
}
