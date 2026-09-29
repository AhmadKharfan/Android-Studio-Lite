package com.ahmadkharfan.androidstudiolite.domain.repository

/**
 * How GitHub Actions builds get access to GitHub.
 *
 * With a GitHub App configured, builds use their own sign-in ([authenticator] and [credentials]): a token
 * limited to the build repository and to the App's permissions, separate from the Git features' sign-in.
 * Without one, builds fall back to the Git sign-in and [usesGitHubApp] is false.
 */
interface GitHubBuildAccess {
    val usesGitHubApp: Boolean
    val authenticator: GitHubDeviceAuthenticator
    val credentials: GitCredentialStore

    /** Where to install the App on the build repository, when known. */
    val installUrl: String?
}
