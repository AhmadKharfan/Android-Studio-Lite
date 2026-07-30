package com.ahmadkharfan.androidstudiolite.feature.editor.di

import com.ahmadkharfan.androidstudiolite.feature.editor.EditorFeatureApiImpl
import com.ahmadkharfan.androidstudiolite.feature.editor.EditorViewModel
import com.ahmadkharfan.androidstudiolite.feature.editor.api.EditorFeatureApi
import com.ahmadkharfan.androidstudiolite.feature.editor.aichat.AiChatViewModel
import com.ahmadkharfan.androidstudiolite.feature.editor.assets.AssetsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val editorModule = module {
    viewModel { params ->
        AssetsViewModel(projectId = params.get(), projectPathResolver = get())
    }
    single<EditorFeatureApi> { EditorFeatureApiImpl() }
    viewModel { params -> AiChatViewModel(get(), get(), get(), projectId = params.get()) }
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
