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

private val Context.recentProjectsDataStore by preferencesDataStore(name = "recent_projects")

val localDataModule = module {


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
