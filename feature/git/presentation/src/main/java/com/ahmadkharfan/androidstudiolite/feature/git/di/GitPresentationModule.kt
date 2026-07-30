package com.ahmadkharfan.androidstudiolite.feature.git.di

import com.ahmadkharfan.androidstudiolite.feature.git.GitPanelApiImpl
import com.ahmadkharfan.androidstudiolite.feature.git.GitPanelViewModel
import com.ahmadkharfan.androidstudiolite.feature.git.api.GitPanelApi
import com.ahmadkharfan.androidstudiolite.feature.git.conflict.GitConflictViewModel
import com.ahmadkharfan.androidstudiolite.feature.git.diff.GitDiffViewModel
import com.ahmadkharfan.androidstudiolite.feature.git.history.GitBlameViewModel
import com.ahmadkharfan.androidstudiolite.feature.git.history.GitHistoryViewModel
import com.ahmadkharfan.androidstudiolite.feature.git.refs.GitRefsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * What :feature:git:presentation contributes, including the binding of its own contract.
 *
 * The repository, credential-store and authenticator bindings stay in :app: they wire :domain
 * contracts to :data implementations, which is the composition root's job and something a feature
 * module is not permitted to see.
 */
val gitPresentationModule = module {
    single<GitPanelApi> { GitPanelApiImpl() }

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
}
