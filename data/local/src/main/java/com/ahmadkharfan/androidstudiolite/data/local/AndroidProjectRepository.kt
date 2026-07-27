package com.ahmadkharfan.androidstudiolite.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ahmadkharfan.androidstudiolite.data.templates.ProjectTemplateEngine
import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeType
import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.Project
import com.ahmadkharfan.androidstudiolite.domain.model.TemplateLanguage
import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class AndroidProjectRepository(
    private val projectsRoot: File,
    private val dataStore: DataStore<Preferences>,
    private val changeBus: FileChangeBus,
    private val templateEngine: ProjectTemplateEngine = ProjectTemplateEngine(),
    private val clock: () -> Long = System::currentTimeMillis,
) : ProjectRepository {

    override fun observeRecentProjects(): Flow<List<Project>> =
        dataStore.data
            .map { prefs ->
                ProjectRecordCodec.decode(prefs[KEY].orEmpty()).filter { File(it.path).isDirectory }
            }
            .flowOn(Dispatchers.IO)

    override suspend fun createProject(spec: NewProjectSpec): Project =
        withContext(Dispatchers.IO) {
            val dir = uniqueProjectDir(spec.name, spec.saveLocation)
            val result = templateEngine.generate(spec, dir)
            val project = Project(
                id = dir.name,
                name = spec.name,
                path = dir.absolutePath,
                language = if (result.language == TemplateLanguage.JAVA) "Java" else "Kotlin",
                lastOpenedMillis = clock(),
                packageName = spec.packageName,
            )
            upsert(project)
            changeBus.emit(FileChangeType.CREATED, dir.absolutePath)
            project
        }

    override suspend fun registerExistingProject(path: File): Project = withContext(Dispatchers.IO) {
        val dir = path.absoluteFile
        require(dir.isDirectory) { "Not a directory: ${dir.absolutePath}" }
        val projects = current()
        val alreadyRegistered = projects.firstOrNull { File(it.path).absoluteFile == dir }
        val project = Project(
            id = alreadyRegistered?.id ?: uniqueProjectId(dir.name, projects),
            name = alreadyRegistered?.name ?: dir.name.ifBlank { "Project" },
            path = dir.absolutePath,
            language = ProjectLanguageDetector.detectLanguage(dir),
            lastOpenedMillis = clock(),
            packageName = alreadyRegistered?.packageName,
            buildable = isGradleProject(dir),
        )
        upsert(project)
        project
    }

    override suspend fun openProject(id: String): Project = withContext(Dispatchers.IO) {
        val current = requireProject(id)
        val project = current.copy(
            language = ProjectLanguageDetector.detectLanguage(File(current.path)),
            lastOpenedMillis = clock(),
        )
        upsert(project)
        project
    }

    override suspend fun deleteProject(id: String): Unit = withContext(Dispatchers.IO) {
        val project = current().firstOrNull { it.id == id } ?: return@withContext
        File(project.path).deleteRecursively()
        save(current().filterNot { it.id == id })
        changeBus.emit(FileChangeType.DELETED, project.path)
    }

    override suspend fun renameProject(id: String, newName: String) {
        if (current().none { it.id == id }) return
        save(current().map { if (it.id == id) it.copy(name = newName) else it })
    }

    override suspend fun importProject(sourcePath: String): Project = withContext(Dispatchers.IO) {
        val source = File(sourcePath)
        require(source.isDirectory) { "Not a directory: $sourcePath" }
        require(isGradleProject(source)) { "Not a Gradle project (no settings.gradle): $sourcePath" }
        val dest = uniqueProjectDir(source.name)
        source.copyRecursively(dest, overwrite = false)
        val project = Project(
            id = dest.name,
            name = source.name,
            path = dest.absolutePath,
            language = ProjectLanguageDetector.detectLanguage(dest),
            lastOpenedMillis = clock(),
            buildable = true,
        )
        upsert(project)
        changeBus.emit(FileChangeType.CREATED, dest.absolutePath)
        project
    }


    private suspend fun current(): List<Project> = ProjectRecordCodec.decode(dataStore.data.first()[KEY].orEmpty())

    private suspend fun upsert(project: Project) {
        val next = listOf(project) + current().filterNot { it.id == project.id }
        save(next)
    }

    private suspend fun save(projects: List<Project>) {
        dataStore.edit { it[KEY] = ProjectRecordCodec.encode(projects) }
    }

    private suspend fun requireProject(id: String): Project =
        current().firstOrNull { it.id == id } ?: error("No such project: $id")


    private fun uniqueProjectDir(name: String, saveLocation: String? = null): File {
        val root = saveLocation?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.isAbsolute }
            ?: projectsRoot
        val slug = name.lowercase().map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("").trim('-').ifBlank { "project" }
        var candidate = File(root, slug)
        var suffix = 2
        while (candidate.exists()) {
            candidate = File(root, "$slug-$suffix")
            suffix++
        }
        return candidate
    }

    private fun isGradleProject(dir: File): Boolean =
        File(dir, "settings.gradle.kts").exists() || File(dir, "settings.gradle").exists()

    private fun uniqueProjectId(name: String, projects: List<Project>): String {
        val base = name.ifBlank { "project" }
        val existingIds = projects.mapTo(mutableSetOf()) { it.id }
        var candidate = base
        var suffix = 2
        while (candidate in existingIds) candidate = "$base-${suffix++}"
        return candidate
    }

    private companion object {
        val KEY = stringPreferencesKey("recent_projects")
    }
}
