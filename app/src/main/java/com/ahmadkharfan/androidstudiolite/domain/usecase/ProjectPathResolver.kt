package com.ahmadkharfan.androidstudiolite.domain.usecase

import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import java.io.File

/** Resolves an open project's working tree from its registered, authoritative path. */
class ProjectPathResolver(
    private val projectRepository: ProjectRepository,
) {
    suspend operator fun invoke(projectId: String): File {
        val project = projectRepository.existing().firstOrNull { it.id == projectId }
            ?: error("No such project: $projectId")
        return File(project.path)
    }
}
