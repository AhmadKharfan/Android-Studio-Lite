package com.ahmadkharfan.androidstudiolite.data.githubactions.auth

import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubBuildAccess
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubDeviceAuthenticator

/**
 * The GitHub access GitHub Actions builds run with: the build GitHub App's own sign-in when [appCredentials]
 * is set, otherwise the Git features' sign-in in [gitCredentials].
 */
class GitHubActionsAccess private constructor(
    override val authenticator: GitHubDeviceAuthenticator,
    private val appCredentials: GitHubBuildCredentials?,
    private val gitCredentials: GitCredentialStore,
    override val installUrl: String?,
) : GitHubBuildAccess {

    override val usesGitHubApp: Boolean get() = appCredentials != null

    override val credentials: GitCredentialStore get() = appCredentials ?: gitCredentials

    /** True when GitHub ended the App connection itself; the Git sign-in has no such signal. */
    val signedOutByGitHub: Boolean get() = appCredentials?.wasSignedOutByGitHub ?: false

    /** The token to call GitHub with now, renewed first when the App token is about to expire. */
    suspend fun token(): String? = if (appCredentials != null) {
        appCredentials.accessToken()
    } else {
        gitCredentials.credentialsForHost(GITHUB_HOST)?.token
    }

    companion object {
        private const val GITHUB_HOST = "github.com"

        /** Builds sign in with the GitHub App whose [appSlug] is used for its install link. */
        fun gitHubApp(
            authenticator: GitHubDeviceAuthenticator,
            credentials: GitHubBuildCredentials,
            gitCredentials: GitCredentialStore,
            appSlug: String,
        ) = GitHubActionsAccess(
            authenticator = authenticator,
            appCredentials = credentials,
            gitCredentials = gitCredentials,
            installUrl = appSlug.takeIf { it.isNotBlank() }?.let { "https://github.com/apps/$it/installations/new" },
        )

        /** Builds use the Git features' sign-in (no GitHub App configured). */
        fun gitSignIn(authenticator: GitHubDeviceAuthenticator, gitCredentials: GitCredentialStore) =
            GitHubActionsAccess(authenticator, appCredentials = null, gitCredentials = gitCredentials, installUrl = null)
    }
}
