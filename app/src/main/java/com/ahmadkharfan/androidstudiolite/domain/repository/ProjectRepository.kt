package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.CloneProgress
import com.ahmadkharfan.androidstudiolite.domain.model.Project
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun observeRecentProjects(): Flow<List<Project>>
    suspend fun createProject(name: String, packageName: String, templateId: String): Project
    fun cloneRepository(url: String, branch: String?): Flow<CloneProgress>
    suspend fun openProject(id: String): Project
    suspend fun deleteProject(id: String)
    suspend fun renameProject(id: String, newName: String)
}
