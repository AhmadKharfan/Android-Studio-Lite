package com.ahmadkharfan.androidstudiolite.feature.projects.di

import com.ahmadkharfan.androidstudiolite.core.environment.IdeEnvironmentPaths
import com.ahmadkharfan.androidstudiolite.feature.projects.createproject.CreateProjectViewModel
import com.ahmadkharfan.androidstudiolite.feature.projects.folderpicker.FolderPickerViewModel
import com.ahmadkharfan.androidstudiolite.feature.projects.hub.HubViewModel
import com.ahmadkharfan.androidstudiolite.feature.projects.openproject.OpenProjectViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val projectsModule = module {
    viewModelOf(::HubViewModel)
    viewModelOf(::OpenProjectViewModel)
    viewModelOf(::FolderPickerViewModel)
    viewModel {
        CreateProjectViewModel(
            templateRepository = get(),
            projectRepository = get(),
            defaultLocation = IdeEnvironmentPaths.projectsDir(androidContext()).absolutePath,
        )
    }
}
