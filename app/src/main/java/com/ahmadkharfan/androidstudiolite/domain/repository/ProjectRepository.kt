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

    /**
     * Copies the Gradle project rooted at [sourcePath] (typically picked from external storage) into the
     * on-device projects directory, registers it in the recent list, and returns it. Fails if the source
     * is not a recognizable project (no `settings.gradle[.kts]`).
     */
    suspend fun importProject(sourcePath: String): Project =
        throw UnsupportedOperationException()

    /** Marks the project no longer active; a no-op for stores that only track recency. */
    suspend fun closeProject(id: String) {}
}
