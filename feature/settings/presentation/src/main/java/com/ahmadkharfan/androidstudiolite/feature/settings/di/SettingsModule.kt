package com.ahmadkharfan.androidstudiolite.feature.settings.di

import com.ahmadkharfan.androidstudiolite.feature.settings.SettingsFeatureApiImpl
import com.ahmadkharfan.androidstudiolite.feature.settings.aiagent.AiAgentViewModel
import com.ahmadkharfan.androidstudiolite.feature.settings.api.SettingsFeatureApi
import com.ahmadkharfan.androidstudiolite.feature.settings.buildrun.BuildRunViewModel
import com.ahmadkharfan.androidstudiolite.feature.settings.editor.EditorSettingsViewModel
import com.ahmadkharfan.androidstudiolite.feature.settings.general.GeneralViewModel
import com.ahmadkharfan.androidstudiolite.feature.settings.root.SettingsRootViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val settingsModule = module {
    single<SettingsFeatureApi> { SettingsFeatureApiImpl() }
    viewModelOf(::SettingsRootViewModel)
    viewModelOf(::GeneralViewModel)
    viewModelOf(::EditorSettingsViewModel)
    viewModelOf(::AiAgentViewModel)
    viewModelOf(::BuildRunViewModel)
}
