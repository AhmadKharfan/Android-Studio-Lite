package com.ahmadkharfan.androidstudiolite.di
import com.ahmadkharfan.androidstudiolite.core.environment.IdeEnvironmentPaths
import com.ahmadkharfan.androidstudiolite.data.onboarding.AndroidOnboardingRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.OnboardingRepository
import com.ahmadkharfan.androidstudiolite.feature.createproject.CreateProjectViewModel
import com.ahmadkharfan.androidstudiolite.feature.editor.EditorViewModel
import com.ahmadkharfan.androidstudiolite.feature.editor.aichat.AiChatViewModel
import com.ahmadkharfan.androidstudiolite.feature.folderpicker.FolderPickerViewModel
import com.ahmadkharfan.androidstudiolite.feature.hub.HubViewModel
import com.ahmadkharfan.androidstudiolite.feature.onboarding.complete.CompleteViewModel
import com.ahmadkharfan.androidstudiolite.feature.onboarding.permissions.PermissionsViewModel
import com.ahmadkharfan.androidstudiolite.feature.openproject.OpenProjectViewModel
import com.ahmadkharfan.androidstudiolite.feature.settings.aiagent.AiAgentViewModel
import com.ahmadkharfan.androidstudiolite.feature.settings.buildrun.BuildRunViewModel
import com.ahmadkharfan.androidstudiolite.feature.settings.editor.EditorSettingsViewModel
import com.ahmadkharfan.androidstudiolite.feature.settings.general.GeneralViewModel
import com.ahmadkharfan.androidstudiolite.feature.settings.root.SettingsRootViewModel
import com.ahmadkharfan.androidstudiolite.feature.terminal.TerminalViewModel
import com.ahmadkharfan.androidstudiolite.core.network.NetworkMonitor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

// Repositories still backed by fakes live here. Real ones are bound in their own modules and
// registered from AslApplication: File/Project (localDataModule, T3), Preferences (preferencesModule,
// T6), Terminal (terminalModule, T7), Git (gitModule, T5), Templates (templatesModule, T4), AI
// (aiModule). Those interfaces are intentionally NOT bound here so there are no duplicate Koin definitions.
val dataModule = module {
    single<OnboardingRepository> { AndroidOnboardingRepository(androidContext()) }
    single { NetworkMonitor(androidContext()) }
}
val viewModelModule = module {
    viewModelOf(::PermissionsViewModel)
    viewModelOf(::CompleteViewModel)
    viewModelOf(::HubViewModel)
    viewModelOf(::OpenProjectViewModel)
    // Explicit (not viewModelOf) because the default save location is a plain String the graph can't
    // resolve by type.
    viewModel {
        CreateProjectViewModel(
            templateRepository = get(),
            projectRepository = get(),
            defaultLocation = IdeEnvironmentPaths.projectsDir(androidContext()).absolutePath,
        )
    }
    // CloneRepoViewModel + GitPanelViewModel are bound in gitModule (T5).
    viewModel { params -> AiChatViewModel(get(), get(), projectId = params.get()) }
    viewModelOf(::TerminalViewModel)
    viewModelOf(::FolderPickerViewModel)
    viewModelOf(::SettingsRootViewModel)
    viewModelOf(::GeneralViewModel)
    viewModelOf(::EditorSettingsViewModel)
    viewModelOf(::AiAgentViewModel)
    viewModelOf(::BuildRunViewModel)
    viewModel { params ->
        EditorViewModel(
            projectId = params.get(),
            projectRepository = get(),
            fileTreeRepository = get(),
            fileContentRepository = get(),
            preferencesRepository = get(),
            gradleProjectReader = get(),
            buildRunCoordinator = get(),
            networkMonitor = get(),
        )
    }
}
val appModules = listOf(dataModule, viewModelModule, gradleModule)
