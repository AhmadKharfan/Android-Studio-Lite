package com.ahmadkharfan.androidstudiolite.data.githubactions.auth

import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubDeviceAuthState
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubDeviceAuthenticator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubActionsAccessTest {

    private val credentials = GitHubBuildCredentials(
        vault = object : TokenVault {
            override fun get(key: String): String? = null
            override fun putAll(values: Map<String, String?>) = Unit
            override fun clear() = Unit
        },
        refresher = GitHubTokenRefresher("Iv23test"),
    )

    private fun gitHubApp(slug: String) =
        GitHubActionsAccess.gitHubApp(NoAuthenticator, credentials, NoGitCredentials, appSlug = slug)

    @Test
    fun `the install button opens the github app's own installation page`() {
        val access = gitHubApp("android-studio-lite-builds")

        assertTrue(access.usesGitHubApp)
        assertEquals("https://github.com/apps/android-studio-lite-builds/installations/new", access.installUrl)
    }

    @Test
    fun `without a slug there is no app-specific install link`() {
        assertNull(gitHubApp(" ").installUrl)
    }

    @Test
    fun `git sign-in builds have no github app to install`() {
        val access = GitHubActionsAccess.gitSignIn(NoAuthenticator, NoGitCredentials)

        assertFalse(access.usesGitHubApp)
        assertNull(access.installUrl)
    }

    private object NoAuthenticator : GitHubDeviceAuthenticator {
        override val isConfigured = true
        override fun authenticate(): Flow<GitHubDeviceAuthState> = emptyFlow()
    }

    private object NoGitCredentials : GitCredentialStore {
        override fun credentialsForUrl(url: String): GitCredentials? = null
        override fun credentialsForHost(host: String): GitCredentials? = null
        override fun hasCredentials(host: String): Boolean = false
        override fun save(host: String, credentials: GitCredentials) = Unit
        override fun clear(host: String) = Unit
        override val changes: Flow<Unit> = emptyFlow()
    }
}
