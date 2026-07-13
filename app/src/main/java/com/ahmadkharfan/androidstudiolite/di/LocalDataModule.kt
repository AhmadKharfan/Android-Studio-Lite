package com.ahmadkharfan.androidstudiolite.di

import android.content.Context
import android.os.Environment
import androidx.datastore.preferences.preferencesDataStore
import com.ahmadkharfan.androidstudiolite.core.environment.IdeEnvironmentPaths
import com.ahmadkharfan.androidstudiolite.data.local.AndroidProjectRepository
import com.ahmadkharfan.androidstudiolite.data.local.FileChangeBus
import com.ahmadkharfan.androidstudiolite.data.local.LocalFileContentRepository
import com.ahmadkharfan.androidstudiolite.data.local.LocalFileSystemRepository
import com.ahmadkharfan.androidstudiolite.data.local.LocalFileTreeRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.FileContentRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.FileSystemRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.FileTreeRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** DataStore holding the serialized recent-projects list; kept separate from other feature stores. */
private val Context.recentProjectsDataStore by preferencesDataStore(name = "recent_projects")

/**
 * Real implementations of the file-system / project layer (task T3), replacing the in-memory `Fake*`
 * bindings from [dataModule]. This is registered *after* [appModules] in [com.ahmadkharfan.androidstudiolite.AslApplication]
 * so Koin's default override (last-wins) swaps the fakes out — kept in its own file, and flavor-agnostic,
 * so it never collides with sibling task edits to the shared [dataModule].
 */
val localDataModule = module {
    // One change bus shared by every local repository so a mutation via any of them is observable
    // through all of them (editor external-change detection, live file-tree refresh).
    single { FileChangeBus() }

    single<FileSystemRepository> {
        LocalFileSystemRepository(
            browseRoot = Environment.getExternalStorageDirectory(),
            changeBus = get(),
        )
    }
    single<FileTreeRepository> {
        LocalFileTreeRepository(
            projectsRoot = IdeEnvironmentPaths.projectsDir(androidContext()),
            changeBus = get(),
        )
    }
    single<FileContentRepository> {
        LocalFileContentRepository(changeBus = get())
    }
    single<ProjectRepository> {
        AndroidProjectRepository(
            projectsRoot = IdeEnvironmentPaths.projectsDir(androidContext()),
            dataStore = androidContext().recentProjectsDataStore,
            changeBus = get(),
            templateEngine = get(),
        )
    }
}
