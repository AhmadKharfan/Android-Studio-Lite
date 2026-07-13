package com.ahmadkharfan.androidstudiolite.data.fake

import com.ahmadkharfan.androidstudiolite.domain.model.CloneProgress
import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.Project
import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import java.util.UUID

class FakeProjectRepository : ProjectRepository {

    private val now = System.currentTimeMillis()
    private val projects = MutableStateFlow(
        listOf(
            Project(
                id = "myapplication",
                name = "MyApplication",
                path = "~/projects/MyApplication",
                language = "Kotlin",
                lastOpenedMillis = now - 2 * 60 * 60 * 1000L,
            ),
            Project(
                id = "weatherwidget",
                name = "WeatherWidget",
                path = "~/projects/WeatherWidget",
                language = "Compose",
                lastOpenedMillis = now - 24 * 60 * 60 * 1000L,
            ),
        ),
    )

    override fun observeRecentProjects(): StateFlow<List<Project>> = projects

    override suspend fun createProject(spec: NewProjectSpec): Project {
        val project = Project(
            id = UUID.randomUUID().toString(),
            name = spec.name,
            path = "~/projects/${spec.name}",
            language = "Kotlin",
            lastOpenedMillis = System.currentTimeMillis(),
        )
        projects.value = listOf(project) + projects.value
        return project
    }

    override fun cloneRepository(url: String, branch: String?): Flow<CloneProgress> = flow {
        emit(CloneProgress(fraction = 0f, message = "Resolving $url"))
        delay(400)
        emit(CloneProgress(fraction = 0.35f, message = "Receiving objects"))
        delay(500)
        emit(CloneProgress(fraction = 0.75f, message = "Resolving deltas"))
        delay(400)
        val name = url.substringAfterLast('/').removeSuffix(".git").ifBlank { "repository" }
        val project = Project(
            id = UUID.randomUUID().toString(),
            name = name,
            path = "~/projects/$name",
            language = "Kotlin",
            lastOpenedMillis = System.currentTimeMillis(),
        )
        projects.value = listOf(project) + projects.value
        emit(CloneProgress(fraction = 1f, message = "Done", clonedProjectId = project.id))
    }

    override suspend fun openProject(id: String): Project {
        val project = projects.value.first { it.id == id }
        val updated = project.copy(lastOpenedMillis = System.currentTimeMillis())
        projects.value = projects.value.map { if (it.id == id) updated else it }
        return updated
    }

    override suspend fun deleteProject(id: String) {
        projects.value = projects.value.filterNot { it.id == id }
    }

    override suspend fun renameProject(id: String, newName: String) {
        projects.value = projects.value.map { if (it.id == id) it.copy(name = newName) else it }
    }
}
