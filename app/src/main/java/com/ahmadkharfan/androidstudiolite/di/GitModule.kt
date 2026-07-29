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
import com.ahmadkharfan.androidstudiolite.feature.clonerepo.CloneRepoViewModel
import com.ahmadkharfan.androidstudiolite.feature.editor.assets.AssetsViewModel
import com.ahmadkharfan.androidstudiolite.feature.git.api.GitPanelApi
import com.ahmadkharfan.androidstudiolite.feature.editor.git.GitPanelApiImpl
import com.ahmadkharfan.androidstudiolite.feature.editor.git.GitPanelViewModel
import com.ahmadkharfan.androidstudiolite.feature.editor.git.diff.GitDiffViewModel
import com.ahmadkharfan.androidstudiolite.feature.editor.git.history.GitBlameViewModel
import com.ahmadkharfan.androidstudiolite.feature.editor.git.history.GitHistoryViewModel
import com.ahmadkharfan.androidstudiolite.feature.editor.git.refs.GitRefsViewModel
import com.ahmadkharfan.androidstudiolite.feature.editor.git.conflict.GitConflictViewModel
import com.ahmadkharfan.androidstudiolite.feature.settings.gitauth.GitAuthSettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

private val Context.gitAuthorDataStore: DataStore<Preferences> by preferencesDataStore(name = "git_author")

val gitModule = module {
    single<GitPanelApi> { GitPanelApiImpl() }
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


    viewModelOf(::CloneRepoViewModel)


    viewModel { params ->
        GitPanelViewModel(
            projectId = params.get(),
            projectPathResolver = get(),
            gitRepository = get(),
            operationMonitor = get(),
            credentialStore = get(),
            authenticator = get(),
        )
    }
    viewModel { params ->
        GitDiffViewModel(
            projectId = params[0],
            path = params[1],
            target = params[2],
            commitId = params.get<String>(3).takeIf { it.isNotBlank() },
            projectPathResolver = get(),
            gitRepository = get(),
        )
    }
    viewModel { params ->
        GitHistoryViewModel(
            projectId = params[0],

            requestedPath = params.get<String>(1).takeIf { it.isNotBlank() },
            projectPathResolver = get(),
            gitRepository = get(),
        )
    }
    viewModel { params ->
        GitBlameViewModel(
            projectId = params[0],
            requestedPath = params[1],
            projectPathResolver = get(),
            gitRepository = get(),
        )
    }
    viewModel { params ->
        GitRefsViewModel(
            projectId = params[0],
            mode = params[1],
            projectPathResolver = get(),
            gitRepository = get(),
            credentialStore = get(),
            authenticator = get(),
        )
    }
    viewModel { params ->
        GitConflictViewModel(
            projectId = params.get(),
            projectPathResolver = get(),
            gitRepository = get(),
        )
    }

    viewModel { params ->
        AssetsViewModel(
            projectId = params.get(),
            projectPathResolver = get(),
        )
    }
    viewModel {
        GitAuthSettingsViewModel(
            credentialStore = get(),
            authenticator = get(),
            gitAuthorStore = get(),
        )
    }
}
