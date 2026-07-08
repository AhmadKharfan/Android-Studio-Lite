package com.example.androidstudiolite.domain.usecase

import com.example.androidstudiolite.domain.model.CloneProgress
import com.example.androidstudiolite.domain.model.Project
import com.example.androidstudiolite.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetRecentProjectsUseCase(private val repository: ProjectRepository) {
    operator fun invoke(): Flow<List<Project>> = repository.observeRecentProjects()
}

/** Most-recently-opened project, used for the hub's "Resume X where you left off?" banner. */
class GetResumeProjectUseCase(private val repository: ProjectRepository) {
    operator fun invoke(): Flow<Project?> = repository.observeRecentProjects().map { it.maxByOrNull { p -> p.lastOpenedMillis ?: 0L } }
}

class CreateProjectUseCase(private val repository: ProjectRepository) {
    suspend operator fun invoke(name: String, packageName: String, templateId: String): Project =
        repository.createProject(name, packageName, templateId)
}

class CloneRepositoryUseCase(private val repository: ProjectRepository) {
    operator fun invoke(url: String, branch: String?): Flow<CloneProgress> = repository.cloneRepository(url, branch)
}

class OpenProjectUseCase(private val repository: ProjectRepository) {
    suspend operator fun invoke(id: String): Project = repository.openProject(id)
}
