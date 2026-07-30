package com.ahmadkharfan.androidstudiolite.di

import android.content.Context
import androidx.datastore.core.DataStore
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffTarget
import com.ahmadkharfan.androidstudiolite.domain.usecase.CloneProjectUseCase
import com.ahmadkharfan.androidstudiolite.feature.editor.EditorViewModel
import com.ahmadkharfan.androidstudiolite.feature.editor.aichat.AiChatViewModel
import com.ahmadkharfan.androidstudiolite.feature.editor.assets.AssetsViewModel
import com.ahmadkharfan.androidstudiolite.feature.git.GitPanelViewModel
import com.ahmadkharfan.androidstudiolite.feature.git.conflict.GitConflictViewModel
import com.ahmadkharfan.androidstudiolite.feature.git.diff.GitDiffViewModel
import com.ahmadkharfan.androidstudiolite.feature.git.history.GitBlameViewModel
import com.ahmadkharfan.androidstudiolite.feature.git.history.GitHistoryViewModel
import com.ahmadkharfan.androidstudiolite.feature.git.refs.GitRefsMode
import com.ahmadkharfan.androidstudiolite.feature.git.refs.GitRefsViewModel
import com.ahmadkharfan.androidstudiolite.feature.terminal.TerminalSessionManager
import java.io.File
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class KoinModuleGraphTest {
    @Test
    fun allApplicationModulesHaveResolvableDefinitions() {
        module {
            includes(allModules)
        }.verify(
            extraTypes = listOf(
                Context::class,
                DataStore::class,
                File::class,
            ),
            injections = injectedParameters(
                definition<AiChatViewModel>(String::class),
                definition<EditorViewModel>(String::class),
                definition<CloneProjectUseCase>(Function0::class),
                definition<TerminalSessionManager>(Function1::class),
                definition<GitPanelViewModel>(String::class),
                definition<GitDiffViewModel>(String::class, GitDiffTarget::class),
                definition<GitHistoryViewModel>(String::class),
                definition<GitBlameViewModel>(String::class),
                definition<GitRefsViewModel>(String::class, GitRefsMode::class),
                definition<GitConflictViewModel>(String::class),
                definition<AssetsViewModel>(String::class),
            ),
        )
    }
}
