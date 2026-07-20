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


val dataModule = module {
    single<OnboardingRepository> { AndroidOnboardingRepository(androidContext()) }
    single { NetworkMonitor(androidContext()) }
}
val viewModelModule = module {
    viewModelOf(::PermissionsViewModel)
    viewModelOf(::CompleteViewModel)
    viewModelOf(::HubViewModel)
    viewModelOf(::OpenProjectViewModel)


    viewModel {
        CreateProjectViewModel(
            templateRepository = get(),
            projectRepository = get(),
            defaultLocation = IdeEnvironmentPaths.projectsDir(androidContext()).absolutePath,
        )
    }

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
