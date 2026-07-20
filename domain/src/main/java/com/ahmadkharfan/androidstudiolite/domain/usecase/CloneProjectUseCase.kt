package com.ahmadkharfan.androidstudiolite.domain.usecase

import com.ahmadkharfan.androidstudiolite.domain.model.CloneOptions
import com.ahmadkharfan.androidstudiolite.domain.model.CloneProgress
import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.repository.GitRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class CloneProjectUseCase(
    private val projectsDir: () -> File,
    private val gitRepository: GitRepository,
    private val projectRepository: ProjectRepository,
) {
    fun clone(
        url: String,
        options: CloneOptions,
        credentials: GitCredentials?,
    ): Flow<CloneProgress> = flow {
        val destination = uniqueDestination(deriveRepositoryName(url))
        var registered = false
        try {
            gitRepository.clone(url, destination, options, credentials).collect { emit(it) }
            currentCoroutineContext().ensureActive()
            val project = try {
                projectRepository.registerExistingProject(destination)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                throw CloneRegistrationException(destination, cause)
            }
            registered = true
            emit(CloneProgress(fraction = 1f, message = "Done", clonedProjectId = project.id))
        } catch (cause: CancellationException) {
            if (!registered) destination.deleteRecursively()
            throw cause
        }
    }

    private fun uniqueDestination(name: String): File {
        val root = projectsDir().apply { mkdirs() }
        var candidate = File(root, name)
        var suffix = 2
        while (candidate.exists()) candidate = File(root, "$name-${suffix++}")
        return candidate
    }

    private fun deriveRepositoryName(url: String): String =
        url.trim().trimEnd('/').substringAfterLast('/').removeSuffix(".git").ifBlank { "repository" }
}

class CloneRegistrationException(
    val clonedDirectory: File,
    cause: Throwable,
) : Exception(
    "Clone completed, but the project could not be registered at ${clonedDirectory.absolutePath}",
    cause,
)
