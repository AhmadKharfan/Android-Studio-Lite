package com.ahmadkharfan.androidstudiolite.di

import com.ahmadkharfan.androidstudiolite.core.environment.IdeEnvironmentPaths
import com.ahmadkharfan.androidstudiolite.data.local.EncryptedGitCredentialStore
import com.ahmadkharfan.androidstudiolite.data.local.JGitGitRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import com.ahmadkharfan.androidstudiolite.domain.repository.GitRepository
import com.ahmadkharfan.androidstudiolite.feature.clonerepo.CloneRepoViewModel
import com.ahmadkharfan.androidstudiolite.feature.editor.git.GitPanelViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import java.io.File

/**
 * T5 — JGit-backed git integration. Kept in its own module (registered from [AslApplication]) so the
 * central [KoinModules] file stays untouched by this task.
 */
val gitModule = module {
    single<GitCredentialStore> { EncryptedGitCredentialStore(androidContext()) }
    single<GitRepository> {
        val context = androidContext()
        JGitGitRepository(
            projectsHome = { IdeEnvironmentPaths.projectsHome(context) },
            credentialStore = get(),
        )
    }

    // The clone screen drives a real JGit clone (+ optional token persistence) instead of the fake.
    viewModelOf(::CloneRepoViewModel)

    // The in-editor git panel is scoped to the open project's working tree, resolved from its id.
    viewModel { params ->
        GitPanelViewModel(
            repoDir = File(IdeEnvironmentPaths.projectsHome(androidContext()), params.get<String>()),
            gitRepository = get(),
        )
    }
}
