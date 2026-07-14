package com.ahmadkharfan.androidstudiolite.di
import com.ahmadkharfan.androidstudiolite.data.fake.FakeAiAgentRepository
import com.ahmadkharfan.androidstudiolite.data.fake.FakeAiChatRepository
import com.ahmadkharfan.androidstudiolite.data.fake.FakeTemplateRepository
import com.ahmadkharfan.androidstudiolite.data.environment.AndroidIdeEnvironmentRepository
import com.ahmadkharfan.androidstudiolite.data.onboarding.AndroidOnboardingRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.AiAgentRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.AiChatRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.IdeEnvironmentRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.OnboardingRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.TemplateRepository
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

// Repositories still backed by fakes live here. Real ones are bound in their own modules and
// registered from AslApplication: File/Project (localDataModule, T3), Preferences (preferencesModule,
// T6), Terminal (terminalModule, T7), Git (gitModule, T5). Those interfaces are intentionally NOT
// bound here so there are no duplicate Koin definitions.
val dataModule = module {
    single<OnboardingRepository> { AndroidOnboardingRepository(androidContext()) }
    single<IdeEnvironmentRepository> { AndroidIdeEnvironmentRepository(androidContext()) }
    single { NetworkMonitor(androidContext()) }
    single<TemplateRepository> { FakeTemplateRepository() }
    single<AiAgentRepository> { FakeAiAgentRepository() }
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
            buildRunCoordinator = get(),
        )
    }
}
val appModules = listOf(dataModule, viewModelModule, gradleModule)
