package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File

interface ProjectRepository {
    fun observeRecentProjects(): Flow<List<Project>>

    suspend fun existing(): List<Project> = observeRecentProjects().first()

    suspend fun createProject(spec: NewProjectSpec): Project

    suspend fun createProject(name: String, packageName: String, templateId: String): Project =
        createProject(NewProjectSpec(name = name, packageName = packageName, templateId = templateId))
    suspend fun registerExistingProject(path: File): Project

    suspend fun openProject(id: String): Project
    suspend fun deleteProject(id: String)
    suspend fun renameProject(id: String, newName: String)

    suspend fun importProject(sourcePath: String): Project =
        throw UnsupportedOperationException()

    suspend fun closeProject(id: String) {}
}
