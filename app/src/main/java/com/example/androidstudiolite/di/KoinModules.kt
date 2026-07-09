package com.example.androidstudiolite.di

import com.example.androidstudiolite.data.fake.FakeAiAgentRepository
import com.example.androidstudiolite.data.fake.FakeAiChatRepository
import com.example.androidstudiolite.data.fake.FakeFileSystemRepository
import com.example.androidstudiolite.data.fake.FakeFileTreeRepository
import com.example.androidstudiolite.data.fake.FakeGitRepository
import com.example.androidstudiolite.data.fake.FakeIdeConfigRepository
import com.example.androidstudiolite.data.fake.FakeOnboardingRepository
import com.example.androidstudiolite.data.fake.FakePreferencesRepository
import com.example.androidstudiolite.data.fake.FakeProjectRepository
import com.example.androidstudiolite.data.fake.FakeTemplateRepository
import com.example.androidstudiolite.data.fake.FakeTerminalRepository
import com.example.androidstudiolite.domain.repository.AiAgentRepository
import com.example.androidstudiolite.domain.repository.AiChatRepository
import com.example.androidstudiolite.domain.repository.FileSystemRepository
import com.example.androidstudiolite.domain.repository.FileTreeRepository
import com.example.androidstudiolite.domain.repository.GitRepository
import com.example.androidstudiolite.domain.repository.IdeConfigRepository
import com.example.androidstudiolite.domain.repository.OnboardingRepository
import com.example.androidstudiolite.domain.repository.PreferencesRepository
import com.example.androidstudiolite.domain.repository.ProjectRepository
import com.example.androidstudiolite.domain.repository.TemplateRepository
import com.example.androidstudiolite.domain.repository.TerminalRepository
import com.example.androidstudiolite.feature.clonerepo.CloneRepoViewModel
import com.example.androidstudiolite.feature.createproject.CreateProjectViewModel
import com.example.androidstudiolite.feature.editor.EditorViewModel
import com.example.androidstudiolite.feature.editor.aichat.AiChatViewModel
import com.example.androidstudiolite.feature.editor.git.GitPanelViewModel
import com.example.androidstudiolite.feature.editor.variants.VariantsViewModel
import com.example.androidstudiolite.feature.folderpicker.FolderPickerViewModel
import com.example.androidstudiolite.feature.hub.HubViewModel
import com.example.androidstudiolite.feature.onboarding.complete.CompleteViewModel
import com.example.androidstudiolite.feature.onboarding.permissions.PermissionsViewModel
import com.example.androidstudiolite.feature.onboarding.setup.SetupViewModel
import com.example.androidstudiolite.feature.onboarding.statistics.StatisticsViewModel
import com.example.androidstudiolite.feature.openproject.OpenProjectViewModel
import com.example.androidstudiolite.feature.settings.aiagent.AiAgentViewModel
import com.example.androidstudiolite.feature.settings.buildrun.BuildRunViewModel
import com.example.androidstudiolite.feature.settings.developer.DeveloperOptionsViewModel
import com.example.androidstudiolite.feature.settings.editor.EditorSettingsViewModel
import com.example.androidstudiolite.feature.settings.general.GeneralViewModel
import com.example.androidstudiolite.feature.settings.ideconfig.IdeConfigViewModel
import com.example.androidstudiolite.feature.settings.root.SettingsRootViewModel
import com.example.androidstudiolite.feature.terminal.TerminalViewModel
import com.example.androidstudiolite.feature.uidesigner.DesignerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Data layer: repository implementations bound to their domain interfaces as process-wide singletons.
 * Swap any `Fake*Repository` for a real implementation here without touching domain or UI code.
 */
val dataModule = module {
    single<OnboardingRepository> { FakeOnboardingRepository() }
    single<ProjectRepository> { FakeProjectRepository() }
    single<TemplateRepository> { FakeTemplateRepository() }
    single<FileTreeRepository> { FakeFileTreeRepository() }
    single<PreferencesRepository> { FakePreferencesRepository() }
    single<AiAgentRepository> { FakeAiAgentRepository() }
    single<FileSystemRepository> { FakeFileSystemRepository() }
    single<IdeConfigRepository> { FakeIdeConfigRepository() }
    single<TerminalRepository> { FakeTerminalRepository() }
    single<GitRepository> { FakeGitRepository() }
    single<AiChatRepository> { FakeAiChatRepository() }
}

/** Presentation layer: one ViewModel per screen. [EditorViewModel] receives its `projectId` at call site. */
val viewModelModule = module {
    viewModelOf(::StatisticsViewModel)
    viewModelOf(::PermissionsViewModel)
    viewModelOf(::SetupViewModel)
    viewModelOf(::CompleteViewModel)
    viewModelOf(::HubViewModel)
    viewModelOf(::OpenProjectViewModel)
    viewModelOf(::CloneRepoViewModel)
    viewModelOf(::CreateProjectViewModel)
    viewModelOf(::GitPanelViewModel)
    viewModelOf(::AiChatViewModel)
    viewModelOf(::VariantsViewModel)
    viewModelOf(::TerminalViewModel)
    viewModelOf(::DesignerViewModel)
    viewModelOf(::FolderPickerViewModel)
    viewModelOf(::SettingsRootViewModel)
    viewModelOf(::GeneralViewModel)
    viewModelOf(::EditorSettingsViewModel)
    viewModelOf(::AiAgentViewModel)
    viewModelOf(::BuildRunViewModel)
    viewModelOf(::IdeConfigViewModel)
    viewModelOf(::DeveloperOptionsViewModel)
    viewModel { params ->
        EditorViewModel(
            projectId = params.get(),
            projectRepository = get(),
            fileTreeRepository = get(),
        )
    }
}

/** All Koin modules, wired up in [com.example.androidstudiolite.AslApplication]. */
val appModules = listOf(dataModule, viewModelModule)
