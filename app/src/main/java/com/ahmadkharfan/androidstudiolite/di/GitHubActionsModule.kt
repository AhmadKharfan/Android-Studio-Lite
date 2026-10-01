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
import com.ahmadkharfan.androidstudiolite.data.githubactions.build.GitHubActionsReadiness
import com.ahmadkharfan.androidstudiolite.data.githubactions.build.RepositorySetup
import com.ahmadkharfan.androidstudiolite.data.githubactions.readiness.BuildStorageMemory
import com.ahmadkharfan.androidstudiolite.data.githubactions.readiness.CloudBuildReadinessChecker
import com.ahmadkharfan.androidstudiolite.data.githubactions.readiness.CloudBuildReadinessMonitor
import com.ahmadkharfan.androidstudiolite.data.githubactions.readiness.GitHubBuildStorageCreator
import com.ahmadkharfan.androidstudiolite.data.githubactions.readiness.ReadinessSignals
import com.ahmadkharfan.androidstudiolite.data.githubactions.readiness.SharedPreferencesBuildStorageMemory
import com.ahmadkharfan.androidstudiolite.data.githubactions.snapshot.SourceSnapshotPusher
import com.ahmadkharfan.androidstudiolite.data.remote.github.GitHubDeviceFlowAuthenticator
import com.ahmadkharfan.androidstudiolite.core.network.NetworkMonitor
import com.ahmadkharfan.androidstudiolite.core.gitauth.cloudbuild.CloudBuildServices
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildReadiness
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubBuildAccess
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    single<BuildStorageMemory> { SharedPreferencesBuildStorageMemory(androidContext()) }
    single {
        val access = get<GitHubActionsAccess>()
        val config = get<GitHubActionsConfig>()
        val network = get<NetworkMonitor>()
        CloudBuildReadinessChecker(
            api = get(),
            token = { access.token() },
            repositoryName = config.repositoryName,
            setup = config.repositorySetup,
            memory = get(),
            signals = ReadinessSignals(isOnline = network::isOnline, signedOutByGitHub = { access.signedOutByGitHub }),
        )
    }
    single<CloudBuildReadiness> {
        val checker = get<CloudBuildReadinessChecker>()
        CloudBuildReadinessMonitor(
            checker = { checker.check() },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            // Connecting, disconnecting, or GitHub ending the connection all change readiness.
            recheckTriggers = get<GitHubActionsAccess>().credentials.changes,
            onlineChanges = get<NetworkMonitor>().observeOnline(),
        )
    }
    single {
        val access = get<GitHubActionsAccess>()
        val gitCredentials = get<GitCredentialStore>()
        val config = get<GitHubActionsConfig>()
        CloudBuildServices(
            readiness = get(),
            access = access,
            // With the GitHub App, the Git sign-in (OAuth, `repo` scope) creates the build storage: the App
            // can't. Without the App, builds create it themselves with the Git sign-in.
            storageCreator = if (access.usesGitHubApp) {
                GitHubBuildStorageCreator(
                    buildApi = get(),
                    signInApi = GitHubApiClient(token = { gitCredentials.credentialsForHost(GITHUB_HOST)?.token }),
                    hasSignIn = { gitCredentials.hasCredentials(GITHUB_HOST) },
                    repositoryName = config.repositoryName,
                    memory = get(),
                    isOnline = get<NetworkMonitor>()::isOnline,
                )
            } else {
                null
            },
        )
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
            readiness = GitHubActionsReadiness(storageMemory = get(), shared = get()),
        )
    }
}

private const val GITHUB_HOST = "github.com"
