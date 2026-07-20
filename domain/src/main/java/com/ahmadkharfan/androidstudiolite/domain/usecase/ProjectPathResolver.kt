package com.ahmadkharfan.androidstudiolite.domain.usecase

import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import java.io.File

class ProjectPathResolver(
    private val projectRepository: ProjectRepository,
) {
    suspend operator fun invoke(projectId: String): File {
        val project = projectRepository.existing().firstOrNull { it.id == projectId }
            ?: error("No such project: $projectId")
        return File(project.path)
    }
}
