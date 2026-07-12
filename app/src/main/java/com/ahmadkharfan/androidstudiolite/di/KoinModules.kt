package com.ahmadkharfan.androidstudiolite.di
import com.ahmadkharfan.androidstudiolite.data.fake.FakeAiAgentRepository
import com.ahmadkharfan.androidstudiolite.data.fake.FakeAiChatRepository
import com.ahmadkharfan.androidstudiolite.data.fake.FakeFileContentRepository
import com.ahmadkharfan.androidstudiolite.data.fake.FakeFileSystemRepository
import com.ahmadkharfan.androidstudiolite.data.fake.FakeFileTreeRepository
import com.ahmadkharfan.androidstudiolite.data.fake.FakePreferencesRepository
import com.ahmadkharfan.androidstudiolite.data.fake.FakeProjectRepository
import com.ahmadkharfan.androidstudiolite.data.fake.FakeTemplateRepository
import com.ahmadkharfan.androidstudiolite.data.fake.FakeTerminalRepository
import com.ahmadkharfan.androidstudiolite.data.environment.AndroidIdeEnvironmentRepository
import com.ahmadkharfan.androidstudiolite.data.onboarding.AndroidOnboardingRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.AiAgentRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.AiChatRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.FileContentRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.FileSystemRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.FileTreeRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.IdeEnvironmentRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.OnboardingRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.PreferencesRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.TemplateRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.TerminalRepository
import com.ahmadkharfan.androidstudiolite.feature.createproject.CreateProjectViewModel
import com.ahmadkharfan.androidstudiolite.feature.editor.EditorViewModel
import com.ahmadkharfan.androidstudiolite.feature.editor.aichat.AiChatViewModel
import com.ahmadkharfan.androidstudiolite.feature.editor.variants.VariantsViewModel
import com.ahmadkharfan.androidstudiolite.feature.folderpicker.FolderPickerViewModel
import com.ahmadkharfan.androidstudiolite.feature.hub.HubViewModel
import com.ahmadkharfan.androidstudiolite.feature.onboarding.complete.CompleteViewModel
import com.ahmadkharfan.androidstudiolite.feature.onboarding.permissions.PermissionsViewModel
import com.ahmadkharfan.androidstudiolite.feature.onboarding.setup.SetupViewModel
import com.ahmadkharfan.androidstudiolite.feature.openproject.OpenProjectViewModel
import com.ahmadkharfan.androidstudiolite.feature.settings.aiagent.AiAgentViewModel
import com.ahmadkharfan.androidstudiolite.feature.settings.buildrun.BuildRunViewModel
import com.ahmadkharfan.androidstudiolite.feature.settings.developer.DeveloperOptionsViewModel
import com.ahmadkharfan.androidstudiolite.feature.settings.editor.EditorSettingsViewModel
import com.ahmadkharfan.androidstudiolite.feature.settings.general.GeneralViewModel
import com.ahmadkharfan.androidstudiolite.feature.settings.ideconfig.IdeConfigViewModel
import com.ahmadkharfan.androidstudiolite.feature.settings.root.SettingsRootViewModel
import com.ahmadkharfan.androidstudiolite.feature.terminal.TerminalViewModel
import com.ahmadkharfan.androidstudiolite.feature.uidesigner.DesignerViewModel
import com.ahmadkharfan.androidstudiolite.core.network.NetworkMonitor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
val dataModule = module {
    single<OnboardingRepository> { AndroidOnboardingRepository(androidContext()) }
    single<IdeEnvironmentRepository> { AndroidIdeEnvironmentRepository(androidContext()) }
    single { NetworkMonitor(androidContext()) }
    single<ProjectRepository> { FakeProjectRepository() }
    single<TemplateRepository> { FakeTemplateRepository() }
    single<FileTreeRepository> { FakeFileTreeRepository() }
    single<FileContentRepository> { FakeFileContentRepository() }
    single<PreferencesRepository> { FakePreferencesRepository() }
    single<AiAgentRepository> { FakeAiAgentRepository() }
    single<FileSystemRepository> { FakeFileSystemRepository() }
    single<TerminalRepository> { FakeTerminalRepository() }
    // GitRepository is bound in gitModule (T5, JGit-backed).
    single<AiChatRepository> { FakeAiChatRepository() }
}
val viewModelModule = module {
    viewModelOf(::PermissionsViewModel)
    viewModelOf(::SetupViewModel)
    viewModelOf(::CompleteViewModel)
    viewModelOf(::HubViewModel)
    viewModelOf(::OpenProjectViewModel)
    viewModelOf(::CreateProjectViewModel)
    // CloneRepoViewModel + GitPanelViewModel are bound in gitModule (T5).
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
            fileContentRepository = get(),
            preferencesRepository = get(),
        )
    }
}
val appModules = listOf(dataModule, viewModelModule)
