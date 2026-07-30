package com.ahmadkharfan.androidstudiolite.feature.editor.di

import com.ahmadkharfan.androidstudiolite.feature.editor.EditorFeatureApiImpl
import com.ahmadkharfan.androidstudiolite.feature.editor.EditorViewModel
import com.ahmadkharfan.androidstudiolite.feature.editor.api.EditorFeatureApi
import com.ahmadkharfan.androidstudiolite.feature.editor.aichat.AiChatViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val editorModule = module {
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
