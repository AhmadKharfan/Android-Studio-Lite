package com.ahmadkharfan.androidstudiolite.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.ahmadkharfan.androidstudiolite.BuildConfig
import com.ahmadkharfan.androidstudiolite.core.environment.IdeEnvironmentPaths
import com.ahmadkharfan.androidstudiolite.data.git.EncryptedGitCredentialStore
import com.ahmadkharfan.androidstudiolite.data.git.JGitGitRepository
import com.ahmadkharfan.androidstudiolite.data.remote.github.GitHubDeviceFlowAuthenticator
import com.ahmadkharfan.androidstudiolite.data.git.GitOperationCoordinator
import com.ahmadkharfan.androidstudiolite.data.git.DataStoreGitAuthorStore
import com.ahmadkharfan.androidstudiolite.data.local.DefaultWorkspaceWriteGate
import com.ahmadkharfan.androidstudiolite.domain.repository.GitAuthorStore
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubDeviceAuthenticator
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHistoryRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.GitIntegrationRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.GitRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.GitOperationMonitor
import com.ahmadkharfan.androidstudiolite.domain.repository.WorkspaceWriteGate
import com.ahmadkharfan.androidstudiolite.domain.usecase.CloneProjectUseCase
import com.ahmadkharfan.androidstudiolite.domain.usecase.ProjectPathResolver
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val Context.gitAuthorDataStore: DataStore<Preferences> by preferencesDataStore(name = "git_author")

val gitModule = module {
    single<GitCredentialStore> { EncryptedGitCredentialStore(androidContext()) }
    single<GitHubDeviceAuthenticator> {
        GitHubDeviceFlowAuthenticator(
            clientId = BuildConfig.GITHUB_OAUTH_CLIENT_ID,
            credentialStore = get(),
        )
    }
    single<GitAuthorStore> { DataStoreGitAuthorStore(androidContext().gitAuthorDataStore) }
    single<WorkspaceWriteGate> { DefaultWorkspaceWriteGate() }
    single { GitOperationCoordinator() }
    single<GitOperationMonitor> { get<GitOperationCoordinator>() }
    single<GitRepository> {
        JGitGitRepository(
            credentialStore = get(),
            operationCoordinator = get(),
            fileChangeBus = get(),
            authorStore = get(),
            workspaceWriteGate = get(),
        )
    }
    single<GitHistoryRepository> { get<GitRepository>() }
    single<GitIntegrationRepository> { get<GitRepository>() }
    single { ProjectPathResolver(projectRepository = get()) }
    single {
        val context = androidContext()
        CloneProjectUseCase(
            projectsDir = { IdeEnvironmentPaths.projectsDir(context) },
            gitRepository = get(),
            projectRepository = get(),
        )
    }





}
