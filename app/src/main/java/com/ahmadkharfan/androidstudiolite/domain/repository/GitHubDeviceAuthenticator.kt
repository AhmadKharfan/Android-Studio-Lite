package com.ahmadkharfan.androidstudiolite.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Drives GitHub's OAuth 2.0 Device Authorization flow ("Sign in with GitHub"). The app requests a
 * short user code, the user enters it in a browser at the verification URL, and the app polls until
 * GitHub mints an access token. The resulting token is a normal HTTPS credential and is stored in
 * [GitCredentialStore] for host `github.com`, so every project's push/pull/fetch reuses it.
 */
interface GitHubDeviceAuthenticator {

    /** True when a GitHub OAuth App client id is configured, i.e. the device flow can run. */
    val isConfigured: Boolean

    /**
     * Runs the full device flow, emitting each [GitHubDeviceAuthState] transition. Collecting the
     * flow starts it; cancelling collection aborts polling. On success the access token has already
     * been persisted to the credential store for `github.com`.
     */
    fun authenticate(): Flow<GitHubDeviceAuthState>
}

/** Progress of a GitHub device-flow sign-in. */
sealed interface GitHubDeviceAuthState {

    /** Requesting a device/user code from GitHub. */
    data object RequestingCode : GitHubDeviceAuthState

    /**
     * GitHub returned a code. Show [userCode] to the user and send them to [verificationUri] (open in
     * a browser). Polling for the token continues in the background.
     */
    data class AwaitingAuthorization(
        val userCode: String,
        val verificationUri: String,
    ) : GitHubDeviceAuthState

    /** The user authorized the app; a token was minted and saved. [login] is the GitHub username. */
    data class Success(val login: String?) : GitHubDeviceAuthState

    /** The flow failed or was declined. [message] is user-facing. */
    data class Error(val message: String) : GitHubDeviceAuthState
}
