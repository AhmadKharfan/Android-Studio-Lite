package com.ahmadkharfan.androidstudiolite.data.fake

import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.Project
import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
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

    override suspend fun registerExistingProject(path: File): Project {
        val project = Project(
            id = UUID.randomUUID().toString(),
            name = path.name,
            path = path.absolutePath,
            language = "Kotlin",
            lastOpenedMillis = System.currentTimeMillis(),
        )
        projects.value = listOf(project) + projects.value
        return project
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
