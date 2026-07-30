package com.ahmadkharfan.androidstudiolite.di

import com.ahmadkharfan.androidstudiolite.core.network.NetworkMonitor
import com.ahmadkharfan.androidstudiolite.feature.editor.di.editorModule
import com.ahmadkharfan.androidstudiolite.feature.git.di.gitPresentationModule
import com.ahmadkharfan.androidstudiolite.feature.onboarding.di.onboardingModule
import com.ahmadkharfan.androidstudiolite.feature.projects.di.projectsModule
import com.ahmadkharfan.androidstudiolite.feature.settings.di.settingsModule
import com.ahmadkharfan.androidstudiolite.feature.terminal.di.terminalFeatureModule
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Only genuinely app-scoped bindings live here; each feature declares its own. */
val dataModule = module {
    single { NetworkMonitor(androidContext()) }
}

private val featureModules = listOf(
    onboardingModule,
    settingsModule,
    projectsModule,
    terminalFeatureModule,
    editorModule,
    gitPresentationModule,
)

val appModules = listOf(dataModule, gradleModule)

val allModules = appModules + featureModules + localDataModule + templatesModule +
    preferencesModule + terminalModule + gitModule + remoteModule + buildRunModule + aiModule
