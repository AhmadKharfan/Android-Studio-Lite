package com.ahmadkharfan.androidstudiolite.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.ahmadkharfan.androidstudiolite.BuildConfig
import com.ahmadkharfan.androidstudiolite.core.environment.IdeEnvironmentPaths
import com.ahmadkharfan.androidstudiolite.data.local.EncryptedGitCredentialStore
import com.ahmadkharfan.androidstudiolite.data.local.JGitGitRepository
import com.ahmadkharfan.androidstudiolite.data.remote.github.GitHubDeviceFlowAuthenticator
import com.ahmadkharfan.androidstudiolite.data.local.GitOperationCoordinator
import com.ahmadkharfan.androidstudiolite.data.local.DataStoreGitAuthorStore
import com.ahmadkharfan.androidstudiolite.data.local.DefaultWorkspaceWriteGate
import com.ahmadkharfan.androidstudiolite.domain.repository.GitAuthorStore
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubDeviceAuthenticator
import com.ahmadkharfan.androidstudiolite.domain.repository.GitRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.GitOperationMonitor
import com.ahmadkharfan.androidstudiolite.domain.repository.WorkspaceWriteGate
import com.ahmadkharfan.androidstudiolite.domain.usecase.CloneProjectUseCase
import com.ahmadkharfan.androidstudiolite.domain.usecase.ProjectPathResolver
import com.ahmadkharfan.androidstudiolite.feature.clonerepo.CloneRepoViewModel
import com.ahmadkharfan.androidstudiolite.feature.editor.assets.AssetsViewModel
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

/**
 * T5 — JGit-backed git integration. Kept in its own module (registered from [AslApplication]) so the
 * central [KoinModules] file stays untouched by this task.
 */
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
    single { ProjectPathResolver(projectRepository = get()) }
    single {
        val context = androidContext()
        CloneProjectUseCase(
            projectsDir = { IdeEnvironmentPaths.projectsDir(context) },
            gitRepository = get(),
            projectRepository = get(),
        )
    }

    // The clone screen drives a real JGit clone (+ optional token persistence) instead of the fake.
    viewModelOf(::CloneRepoViewModel)

    // The in-editor git panel is scoped to the open project's working tree, resolved from its id.
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
            projectId = params.get(0),
            path = params.get(1),
            target = params.get(2),
            commitId = params.get<String>(3).takeIf { it.isNotBlank() },
            projectPathResolver = get(),
            gitRepository = get(),
        )
    }
    viewModel { params ->
        GitHistoryViewModel(
            projectId = params.get(0),
            // The path arg is always supplied (empty when browsing full history); treat blank as none.
            requestedPath = params.get<String>(1).takeIf { it.isNotBlank() },
            projectPathResolver = get(),
            gitRepository = get(),
        )
    }
    viewModel { params ->
        GitBlameViewModel(
            projectId = params.get(0),
            requestedPath = params.get(1),
            projectPathResolver = get(),
            gitRepository = get(),
        )
    }
    viewModel { params ->
        GitRefsViewModel(
            projectId = params.get(0),
            mode = params.get(1),
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
    // The Assets panel lists real on-disk resources for the open project, resolved from its id.
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
