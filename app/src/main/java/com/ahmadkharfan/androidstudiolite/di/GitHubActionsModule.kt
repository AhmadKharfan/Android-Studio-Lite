package com.ahmadkharfan.androidstudiolite.di

import com.ahmadkharfan.androidstudiolite.BuildConfig
import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubApiClient
import com.ahmadkharfan.androidstudiolite.data.githubactions.auth.EncryptedTokenVault
import com.ahmadkharfan.androidstudiolite.data.githubactions.auth.GitHubActionsAccess
import com.ahmadkharfan.androidstudiolite.data.githubactions.auth.GitHubBuildCredentials
import com.ahmadkharfan.androidstudiolite.data.githubactions.auth.GitHubTokenRefresher
import com.ahmadkharfan.androidstudiolite.data.githubactions.build.ApkSigning
import com.ahmadkharfan.androidstudiolite.data.githubactions.build.GitHubActionsBuildSystem
import com.ahmadkharfan.androidstudiolite.data.githubactions.build.GitHubActionsConfig
import com.ahmadkharfan.androidstudiolite.data.githubactions.build.RepositorySetup
import com.ahmadkharfan.androidstudiolite.data.githubactions.snapshot.SourceSnapshotPusher
import com.ahmadkharfan.androidstudiolite.data.remote.github.GitHubDeviceFlowAuthenticator
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubBuildAccess
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Builds on GitHub Actions. With a GitHub App configured, builds sign in with it: a token limited to the build
 * repository and the App's permissions. Otherwise they reuse the Git features' GitHub sign-in.
 */
val githubActionsModule = module {
    single {
        val clientId = BuildConfig.GITHUB_APP_CLIENT_ID
        if (clientId.isBlank()) {
            GitHubActionsAccess.gitSignIn(authenticator = get(), gitCredentials = get())
        } else {
            val credentials = GitHubBuildCredentials(EncryptedTokenVault(androidContext()), GitHubTokenRefresher(clientId))
            GitHubActionsAccess.gitHubApp(
                authenticator = GitHubDeviceFlowAuthenticator(
                    clientId = clientId,
                    credentialStore = credentials,
                    scope = "",
                    onGranted = credentials::saveGrant,
                ),
                credentials = credentials,
                gitCredentials = get(),
                appSlug = BuildConfig.GITHUB_APP_SLUG,
            )
        }
    }
    single<GitHubBuildAccess> { get<GitHubActionsAccess>() }
    single { SourceSnapshotPusher(File(androidContext().cacheDir, "gha-snapshots")) }
    single {
        val access = get<GitHubActionsAccess>()
        val downloadDir = File(androidContext().cacheDir, "gha-results")
        if (access.usesGitHubApp) {
            GitHubActionsConfig(
                downloadDir = downloadDir,
                // The App may not create repositories (no Administration permission); the user sets one up.
                repositorySetup = RepositorySetup.Manual(access.installUrl),
                signInMessage = "Connect GitHub for builds in Settings › Build & Run.",
            )
        } else {
            GitHubActionsConfig(downloadDir = downloadDir)
        }
    }
    single { ApkSigning(signer = get(), keystores = get()) }
    single {
        val access = get<GitHubActionsAccess>()
        GitHubApiClient(token = { access.token() })
    }
    single {
        val access = get<GitHubActionsAccess>()
        GitHubActionsBuildSystem(
            api = get(),
            token = { access.token() },
            snapshots = get(),
            inspector = get(),
            config = get(),
            signing = get(),
        )
    }
}
