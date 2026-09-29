package com.ahmadkharfan.androidstudiolite.di

import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubApiClient
import com.ahmadkharfan.androidstudiolite.data.githubactions.build.ApkSigning
import com.ahmadkharfan.androidstudiolite.data.githubactions.build.GitHubActionsBuildSystem
import com.ahmadkharfan.androidstudiolite.data.githubactions.build.GitHubActionsConfig
import com.ahmadkharfan.androidstudiolite.data.githubactions.snapshot.SourceSnapshotPusher
import com.ahmadkharfan.androidstudiolite.data.remote.github.GitHubDeviceFlowAuthenticator
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Builds on GitHub Actions with the token from the GitHub sign-in the Git features already use. */
val githubActionsModule = module {
    single { SourceSnapshotPusher(File(androidContext().cacheDir, "gha-snapshots")) }
    single { GitHubActionsConfig(downloadDir = File(androidContext().cacheDir, "gha-results")) }
    single { ApkSigning(signer = get(), keystores = get()) }
    single {
        val credentials = get<GitCredentialStore>()
        GitHubApiClient(token = { credentials.credentialsForHost(GitHubDeviceFlowAuthenticator.GITHUB_HOST)?.token })
    }
    single {
        val credentials = get<GitCredentialStore>()
        GitHubActionsBuildSystem(
            api = get(),
            token = { credentials.credentialsForHost(GitHubDeviceFlowAuthenticator.GITHUB_HOST)?.token },
            snapshots = get(),
            inspector = get(),
            config = get(),
            signing = get(),
        )
    }
}
