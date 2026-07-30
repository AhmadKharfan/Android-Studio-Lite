package com.ahmadkharfan.androidstudiolite.feature.settings.di

import com.ahmadkharfan.androidstudiolite.feature.settings.SettingsFeatureApiImpl
import com.ahmadkharfan.androidstudiolite.feature.settings.aiagent.AiAgentViewModel
import com.ahmadkharfan.androidstudiolite.feature.settings.api.SettingsFeatureApi
import com.ahmadkharfan.androidstudiolite.feature.settings.buildrun.BuildRunViewModel
import com.ahmadkharfan.androidstudiolite.feature.settings.editor.EditorSettingsViewModel
import com.ahmadkharfan.androidstudiolite.feature.settings.general.GeneralViewModel
import com.ahmadkharfan.androidstudiolite.feature.settings.gitauth.GitAuthSettingsViewModel
import com.ahmadkharfan.androidstudiolite.feature.settings.root.SettingsRootViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val settingsModule = module {
    single<SettingsFeatureApi> { SettingsFeatureApiImpl() }
    viewModel {
        GitAuthSettingsViewModel(
            credentialStore = get(),
            authenticator = get(),
            gitAuthorStore = get(),
        )
    }
    viewModelOf(::SettingsRootViewModel)
    viewModelOf(::GeneralViewModel)
    viewModelOf(::EditorSettingsViewModel)
    viewModelOf(::AiAgentViewModel)
    viewModelOf(::BuildRunViewModel)
}
