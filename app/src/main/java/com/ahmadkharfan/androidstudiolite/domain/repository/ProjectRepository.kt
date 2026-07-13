package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.CloneProgress
import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.Project
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun observeRecentProjects(): Flow<List<Project>>

    /**
     * Generates a project from [spec] (template + all wizard options) on disk and registers it.
     * This is the primary entry point; the legacy three-argument overload delegates here with
     * default options.
     */
    suspend fun createProject(spec: NewProjectSpec): Project

    /** Convenience overload used by callers that only carry a template id (defaults for everything else). */
    suspend fun createProject(name: String, packageName: String, templateId: String): Project =
        createProject(NewProjectSpec(name = name, packageName = packageName, templateId = templateId))
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
