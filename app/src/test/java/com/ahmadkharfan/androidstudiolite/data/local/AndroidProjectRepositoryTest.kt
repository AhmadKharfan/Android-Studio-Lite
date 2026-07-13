package com.ahmadkharfan.androidstudiolite.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.ahmadkharfan.androidstudiolite.domain.model.Project
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class AndroidProjectRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val bus = FileChangeBus()
    private val fakeClock = AtomicLong(1_000L)
    private lateinit var projectsRoot: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: AndroidProjectRepository

    @Before
    fun setup() {
        projectsRoot = tmp.newFolder("projects")
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tmp.newFolder("ds"), "recent.preferences_pb") },
        )
        repo = AndroidProjectRepository(
            projectsRoot = projectsRoot,
            dataStore = dataStore,
            changeBus = bus,
            clock = { fakeClock.getAndIncrement() },
        )
    }

    private suspend fun recents(): List<Project> = repo.observeRecentProjects().first()

    @Test
    fun `createProject scaffolds a detectable project and registers it`() = runBlocking {
        val project = repo.createProject("My App", "com.example.myapp", "empty-views")

        assertEquals("my-app", project.id)
        val dir = File(project.path)
        assertTrue(dir.isDirectory)
        assertTrue(File(dir, "settings.gradle.kts").exists())
        assertTrue(File(dir, "app/src/main/java/com/example/myapp/MainActivity.kt").exists())
        assertEquals(listOf("my-app"), recents().map { it.id })
    }

    @Test
    fun `duplicate names get unique directories`() = runBlocking {
        val first = repo.createProject("App", "com.example.a", "empty-views")
        val second = repo.createProject("App", "com.example.b", "empty-views")

        assertEquals("app", first.id)
        assertEquals("app-2", second.id)
        assertTrue(File(second.path).isDirectory)
    }

    @Test
    fun `recent list is most-recent-first and openProject floats a project up`() = runBlocking {
        val a = repo.createProject("Alpha", "com.example.alpha", "empty-views")
        repo.createProject("Beta", "com.example.beta", "empty-views")

        assertEquals(listOf("beta", "alpha"), recents().map { it.id })

        val reopened = repo.openProject(a.id)

        assertEquals(listOf("alpha", "beta"), recents().map { it.id })
        assertTrue(reopened.lastOpenedMillis!! > a.lastOpenedMillis!!)
    }

    @Test
    fun `deleteProject removes the directory and the recent entry`() = runBlocking {
        val project = repo.createProject("Gone", "com.example.gone", "empty-views")

        repo.deleteProject(project.id)

        assertFalse(File(project.path).exists())
        assertTrue(recents().isEmpty())
    }

    @Test
    fun `renameProject changes only the display name`() = runBlocking {
        val project = repo.createProject("Old Name", "com.example.old", "empty-views")

        repo.renameProject(project.id, "New Name")

        val updated = recents().single()
        assertEquals("New Name", updated.name)
        assertEquals(project.id, updated.id)          // id (directory) is stable
        assertEquals(project.path, updated.path)
        assertTrue(File(project.path).isDirectory)
    }

    @Test
    fun `importProject copies an external gradle project in`() = runBlocking {
        val external = tmp.newFolder("external", "SomeProject")
        File(external, "settings.gradle").writeText("rootProject.name = 'SomeProject'\n")
        File(external, "app/src/main/java/App.java").apply { parentFile?.mkdirs() }.writeText("class App {}\n")

        val project = repo.importProject(external.absolutePath)

        assertEquals("Java", project.language)
        val copied = File(project.path)
        assertTrue(copied.absolutePath.startsWith(projectsRoot.absolutePath))
        assertTrue(File(copied, "settings.gradle").exists())
        assertTrue(File(copied, "app/src/main/java/App.java").exists())
        assertEquals(listOf(project.id), recents().map { it.id })
    }

    @Test
    fun `importProject rejects a non-gradle directory`() = runBlocking {
        val notAProject = tmp.newFolder("external", "random")
        File(notAProject, "readme.txt").writeText("hi\n")

        val error = runCatching { repo.importProject(notAProject.absolutePath) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(recents().isEmpty())
    }

    @Test
    fun `recent entries round-trip fields with delimiter characters`() = runBlocking {
        // Tabs/newlines in the display name must survive the serialized store unescaped.
        val gnarly = "Tab\tAnd\nNewline"
        repo.createProject(gnarly, "com.example.weird", "empty-views")

        assertEquals(gnarly, recents().single().name)
    }
}
