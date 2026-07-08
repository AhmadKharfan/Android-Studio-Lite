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
import com.example.androidstudiolite.domain.usecase.CloneRepositoryUseCase
import com.example.androidstudiolite.domain.usecase.CommitGitChangesUseCase
import com.example.androidstudiolite.domain.usecase.CompleteOnboardingUseCase
import com.example.androidstudiolite.domain.usecase.CreateProjectUseCase
import com.example.androidstudiolite.domain.usecase.ExecuteTerminalCommandUseCase
import com.example.androidstudiolite.domain.usecase.GetFileTreeUseCase
import com.example.androidstudiolite.domain.usecase.GetFolderTreeUseCase
import com.example.androidstudiolite.domain.usecase.GetGitDiffUseCase
import com.example.androidstudiolite.domain.usecase.GetProjectTemplatesUseCase
import com.example.androidstudiolite.domain.usecase.GetRecentProjectsUseCase
import com.example.androidstudiolite.domain.usecase.GetResumeProjectUseCase
import com.example.androidstudiolite.domain.usecase.InstallIdeComponentUseCase
import com.example.androidstudiolite.domain.usecase.MarkChatMessageAppliedUseCase
import com.example.androidstudiolite.domain.usecase.MarkSetupCompleteUseCase
import com.example.androidstudiolite.domain.usecase.ObserveAiAgentSettingsUseCase
import com.example.androidstudiolite.domain.usecase.ObserveChatMessagesUseCase
import com.example.androidstudiolite.domain.usecase.ObserveGitStateUseCase
import com.example.androidstudiolite.domain.usecase.ObserveIdeConfigStateUseCase
import com.example.androidstudiolite.domain.usecase.ObserveOnboardingStateUseCase
import com.example.androidstudiolite.domain.usecase.ObservePreferencesUseCase
import com.example.androidstudiolite.domain.usecase.OpenProjectUseCase
import com.example.androidstudiolite.domain.usecase.SendChatMessageUseCase
import com.example.androidstudiolite.domain.usecase.SetAiAgentEnabledUseCase
import com.example.androidstudiolite.domain.usecase.SetAiAgentInstructionsUseCase
import com.example.androidstudiolite.domain.usecase.SetAiProviderApiKeyUseCase
import com.example.androidstudiolite.domain.usecase.SetGitCommitMessageUseCase
import com.example.androidstudiolite.domain.usecase.SetNetworkAvailableUseCase
import com.example.androidstudiolite.domain.usecase.SetOfflineModeUseCase
import com.example.androidstudiolite.domain.usecase.TestAiProviderApiKeyUseCase
import com.example.androidstudiolite.domain.usecase.UpdateAccentUseCase
import com.example.androidstudiolite.domain.usecase.UpdateAutoOpenLastProjectUseCase
import com.example.androidstudiolite.domain.usecase.UpdateEditorFontSizeUseCase
import com.example.androidstudiolite.domain.usecase.UpdateEditorThemeUseCase
import com.example.androidstudiolite.domain.usecase.UpdateLanguageUseCase
import com.example.androidstudiolite.domain.usecase.UpdatePermissionUseCase
import com.example.androidstudiolite.domain.usecase.UpdatePreferencesUseCase
import com.example.androidstudiolite.domain.usecase.UpdateShareUsageStatsUseCase
import com.example.androidstudiolite.domain.usecase.UpdateSnowfallEasterEggUseCase
import com.example.androidstudiolite.domain.usecase.UpdateThemeModeUseCase
import com.example.androidstudiolite.domain.usecase.ValidateProjectNameUseCase
import com.example.androidstudiolite.feature.clonerepo.viewModel.CloneRepoViewModel
import com.example.androidstudiolite.feature.createproject.viewModel.CreateProjectViewModel
import com.example.androidstudiolite.feature.editor.aichat.viewModel.AiChatViewModel
import com.example.androidstudiolite.feature.editor.git.viewModel.GitPanelViewModel
import com.example.androidstudiolite.feature.editor.variants.viewModel.VariantsViewModel
import com.example.androidstudiolite.feature.editor.viewModel.EditorViewModel
import com.example.androidstudiolite.feature.folderpicker.viewModel.FolderPickerViewModel
import com.example.androidstudiolite.feature.hub.viewModel.HubViewModel
import com.example.androidstudiolite.feature.onboarding.complete.viewModel.CompleteViewModel
import com.example.androidstudiolite.feature.onboarding.permissions.viewModel.PermissionsViewModel
import com.example.androidstudiolite.feature.onboarding.setup.viewModel.SetupViewModel
import com.example.androidstudiolite.feature.onboarding.statistics.viewModel.StatisticsViewModel
import com.example.androidstudiolite.feature.openproject.viewModel.OpenProjectViewModel
import com.example.androidstudiolite.feature.settings.aiagent.viewModel.AiAgentViewModel
import com.example.androidstudiolite.feature.settings.buildrun.viewModel.BuildRunViewModel
import com.example.androidstudiolite.feature.settings.developer.viewModel.DeveloperOptionsViewModel
import com.example.androidstudiolite.feature.settings.editor.viewModel.EditorSettingsViewModel
import com.example.androidstudiolite.feature.settings.general.viewModel.GeneralViewModel
import com.example.androidstudiolite.feature.settings.ideconfig.viewModel.IdeConfigViewModel
import com.example.androidstudiolite.feature.settings.root.viewModel.SettingsRootViewModel
import com.example.androidstudiolite.feature.splash.viewModel.SplashViewModel
import com.example.androidstudiolite.feature.terminal.viewModel.TerminalViewModel
import com.example.androidstudiolite.feature.uidesigner.viewModel.DesignerViewModel
import org.koin.core.module.dsl.factoryOf
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

/** Domain layer: use cases resolved fresh per request (they are stateless wrappers over repositories). */
val domainModule = module {
    factoryOf(::GetRecentProjectsUseCase)
    factoryOf(::GetResumeProjectUseCase)
    factoryOf(::CreateProjectUseCase)
    factoryOf(::CloneRepositoryUseCase)
    factoryOf(::OpenProjectUseCase)
    factoryOf(::GetProjectTemplatesUseCase)
    factoryOf(::ValidateProjectNameUseCase)
    factoryOf(::GetFileTreeUseCase)
    factoryOf(::GetFolderTreeUseCase)
    factoryOf(::ObserveGitStateUseCase)
    factoryOf(::GetGitDiffUseCase)
    factoryOf(::SetGitCommitMessageUseCase)
    factoryOf(::CommitGitChangesUseCase)
    factoryOf(::ObserveIdeConfigStateUseCase)
    factoryOf(::InstallIdeComponentUseCase)
    factoryOf(::SetOfflineModeUseCase)
    factoryOf(::SetNetworkAvailableUseCase)
    factoryOf(::ObserveChatMessagesUseCase)
    factoryOf(::SendChatMessageUseCase)
    factoryOf(::MarkChatMessageAppliedUseCase)
    factoryOf(::ObservePreferencesUseCase)
    factoryOf(::UpdateThemeModeUseCase)
    factoryOf(::UpdateEditorFontSizeUseCase)
    factoryOf(::UpdateEditorThemeUseCase)
    factoryOf(::UpdateShareUsageStatsUseCase)
    factoryOf(::UpdateAccentUseCase)
    factoryOf(::UpdateLanguageUseCase)
    factoryOf(::UpdateAutoOpenLastProjectUseCase)
    factoryOf(::UpdateSnowfallEasterEggUseCase)
    factoryOf(::UpdatePreferencesUseCase)
    factoryOf(::ObserveOnboardingStateUseCase)
    factoryOf(::UpdatePermissionUseCase)
    factoryOf(::MarkSetupCompleteUseCase)
    factoryOf(::CompleteOnboardingUseCase)
    factoryOf(::ExecuteTerminalCommandUseCase)
    factoryOf(::ObserveAiAgentSettingsUseCase)
    factoryOf(::SetAiAgentEnabledUseCase)
    factoryOf(::SetAiProviderApiKeyUseCase)
    factoryOf(::TestAiProviderApiKeyUseCase)
    factoryOf(::SetAiAgentInstructionsUseCase)
}

/** Presentation layer: one ViewModel per screen. [EditorViewModel] receives its `projectId` at call site. */
val viewModelModule = module {
    viewModelOf(::SplashViewModel)
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
    viewModel { params -> EditorViewModel(projectId = params.get(), openProject = get(), getFileTree = get()) }
}

/** All Koin modules, wired up in [com.example.androidstudiolite.AslApplication]. */
val appModules = listOf(dataModule, domainModule, viewModelModule)
