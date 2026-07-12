package com.ahmadkharfan.androidstudiolite.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ahmadkharfan.androidstudiolite.domain.model.CloneProgress
import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeType
import com.ahmadkharfan.androidstudiolite.domain.model.Project
import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Real [ProjectRepository]. The recent-projects list is persisted in a DataStore (mirroring the
 * DataStore pattern in [com.ahmadkharfan.androidstudiolite.data.onboarding.AndroidOnboardingRepository])
 * as a single serialized string; all projects live as real directories under [projectsRoot], so a
 * project's id is simply its directory name. Project detection is the presence of a
 * `settings.gradle[.kts]`. Mutations publish events on the shared [changeBus].
 *
 * Git clone is stubbed here — the JGit-backed implementation is task T5.
 */
class AndroidProjectRepository(
    private val projectsRoot: File,
    private val dataStore: DataStore<Preferences>,
    private val changeBus: FileChangeBus,
    private val clock: () -> Long = System::currentTimeMillis,
) : ProjectRepository {

    override fun observeRecentProjects(): Flow<List<Project>> =
        dataStore.data.map { decode(it[KEY].orEmpty()) }

    override suspend fun createProject(name: String, packageName: String, templateId: String): Project =
        withContext(Dispatchers.IO) {
            val dir = uniqueProjectDir(name)
            ProjectScaffold.create(dir, name, packageName.ifBlank { "com.example.app" })
            val project = Project(
                id = dir.name,
                name = name,
                path = dir.absolutePath,
                language = "Kotlin",
                lastOpenedMillis = clock(),
            )
            upsert(project)
            changeBus.emit(FileChangeType.CREATED, dir.absolutePath)
            project
        }

    override fun cloneRepository(url: String, branch: String?): Flow<CloneProgress> = flow {
        // The real JGit clone (and its progress reporting) lands in task T5; until then fail clearly
        // instead of silently doing nothing.
        emit(CloneProgress(fraction = null, message = "Git clone is not available in this build yet"))
    }

    override suspend fun openProject(id: String): Project = withContext(Dispatchers.IO) {
        val project = requireProject(id).copy(lastOpenedMillis = clock())
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
        // Only the display name changes; the directory (and therefore the id) stays stable so open tabs
        // and navigation keep working.
        val project = current().firstOrNull { it.id == id } ?: return
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
            language = detectLanguage(dest),
            lastOpenedMillis = clock(),
        )
        upsert(project)
        changeBus.emit(FileChangeType.CREATED, dest.absolutePath)
        project
    }

    // --- persistence helpers -------------------------------------------------------------------

    private suspend fun current(): List<Project> = decode(dataStore.data.first()[KEY].orEmpty())

    /** Inserts or replaces [project] by id and floats it to the front (most-recent-first ordering). */
    private suspend fun upsert(project: Project) {
        val next = listOf(project) + current().filterNot { it.id == project.id }
        save(next)
    }

    private suspend fun save(projects: List<Project>) {
        dataStore.edit { it[KEY] = encode(projects) }
    }

    private suspend fun requireProject(id: String): Project =
        current().firstOrNull { it.id == id } ?: error("No such project: $id")

    // --- filesystem helpers --------------------------------------------------------------------

    private fun uniqueProjectDir(name: String): File {
        val slug = name.lowercase().map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("").trim('-').ifBlank { "project" }
        var candidate = File(projectsRoot, slug)
        var suffix = 2
        while (candidate.exists()) {
            candidate = File(projectsRoot, "$slug-$suffix")
            suffix++
        }
        return candidate
    }

    private fun isGradleProject(dir: File): Boolean =
        File(dir, "settings.gradle.kts").exists() || File(dir, "settings.gradle").exists()

    private fun detectLanguage(dir: File): String {
        val sources = dir.walkTopDown()
            .onEnter { !LocalFsSupport.isIgnoredDir(it) }
            .filter { it.isFile }
        var sawKotlin = false
        var sawJava = false
        for (file in sources) {
            when (file.extension) {
                "kt", "kts" -> sawKotlin = true
                "java" -> sawJava = true
            }
            if (sawKotlin) break
        }
        return when {
            sawKotlin -> "Kotlin"
            sawJava -> "Java"
            else -> "Kotlin"
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("recent_projects")

        // Records are newline-separated; fields within a record are tab-separated. Both delimiters and
        // the escape char itself are escaped so arbitrary names/paths round-trip safely.
        fun encode(projects: List<Project>): String =
            projects.joinToString("\n") { p ->
                listOf(p.id, p.name, p.path, p.language, (p.lastOpenedMillis ?: 0L).toString())
                    .joinToString("\t") { escape(it) }
            }

        fun decode(raw: String): List<Project> =
            if (raw.isEmpty()) emptyList()
            else raw.split("\n").mapNotNull { line ->
                val parts = line.split("\t")
                if (parts.size < 5) return@mapNotNull null
                Project(
                    id = unescape(parts[0]),
                    name = unescape(parts[1]),
                    path = unescape(parts[2]),
                    language = unescape(parts[3]),
                    lastOpenedMillis = unescape(parts[4]).toLongOrNull()?.takeIf { it > 0L },
                )
            }

        fun escape(s: String): String =
            s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

        fun unescape(s: String): String {
            val out = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (s[i + 1]) {
                        't' -> out.append('\t')
                        'n' -> out.append('\n')
                        '\\' -> out.append('\\')
                        else -> out.append(s[i + 1])
                    }
                    i += 2
                } else {
                    out.append(c)
                    i++
                }
            }
            return out.toString()
        }
    }
}
