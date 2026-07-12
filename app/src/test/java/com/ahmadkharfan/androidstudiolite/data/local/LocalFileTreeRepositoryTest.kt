package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeType
import com.ahmadkharfan.androidstudiolite.domain.model.FileNode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalFileTreeRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val bus = FileChangeBus()
    private lateinit var projectsRoot: File
    private lateinit var repo: LocalFileTreeRepository

    private fun setupProject(): File {
        projectsRoot = tmp.newFolder("projects")
        repo = LocalFileTreeRepository(projectsRoot = projectsRoot, changeBus = bus)
        val project = File(projectsRoot, "demo").apply { mkdirs() }
        File(project, "settings.gradle.kts").writeText("rootProject.name = \"demo\"\n")
        File(project, "app/src/main").apply { mkdirs() }
        File(project, "app/src/main/MainActivity.kt").writeText("class MainActivity\n")
        File(project, "app/build.gradle.kts").writeText("// app\n")
        return project
    }

    @Test
    fun `builds a nested tree resolving project by directory name`() = runBlocking {
        setupProject()

        val tree = repo.getFileTree("demo")

        val names = tree.map { it.name }
        // Directory ("app") sorts before files ("settings.gradle.kts").
        assertEquals(listOf("app", "settings.gradle.kts"), names)
        val app = tree.first { it.name == "app" }
        assertNotNull(app.children)
        val mainActivity = findByName(tree, "MainActivity.kt")
        assertNotNull(mainActivity)
        assertNull(mainActivity!!.children) // files have null children
        assertTrue(mainActivity.id.endsWith("app/src/main/MainActivity.kt"))
    }

    @Test
    fun `resolves project by absolute path too`() = runBlocking {
        val project = setupProject()

        val tree = repo.getFileTree(project.absolutePath)

        assertTrue(tree.any { it.name == "settings.gradle.kts" })
    }

    @Test
    fun `elides build and vcs directories`() = runBlocking {
        val project = setupProject()
        File(project, "build/generated").apply { mkdirs() }
        File(project, "build/generated/R.java").writeText("// generated\n")
        File(project, ".git/HEAD").apply { parentFile?.mkdirs(); writeText("ref\n") }

        val tree = repo.getFileTree("demo")

        assertFalse(tree.any { it.name == "build" })
        assertFalse(tree.any { it.name == ".git" })
    }

    @Test
    fun `create file and directory produce real entries and events`() = runBlocking {
        val project = setupProject()

        val dirEvent = awaitFirst(repo.observeChanges()) {
            repo.createDirectory(project.absolutePath, "newpkg")
        }
        assertEquals(FileChangeType.CREATED, dirEvent.type)
        assertTrue(File(project, "newpkg").isDirectory)

        val newFilePath = repo.createFile(File(project, "newpkg").absolutePath, "Thing.kt")
        assertTrue(File(newFilePath).isFile)
        assertTrue(findByName(repo.getFileTree("demo"), "Thing.kt") != null)
    }

    @Test
    fun `rename keeps the entry in place under a new name`() = runBlocking {
        val project = setupProject()
        val original = File(project, "settings.gradle.kts").absolutePath

        val event = awaitFirst(repo.observeChanges()) {
            repo.rename(original, "settings.gradle")
        }

        assertEquals(FileChangeType.MOVED, event.type)
        assertEquals(original, event.oldPath)
        assertFalse(File(original).exists())
        assertTrue(File(project, "settings.gradle").exists())
    }

    @Test
    fun `move relocates an entry into another directory`() = runBlocking {
        val project = setupProject()
        val target = File(project, "app").absolutePath
        val source = File(project, "settings.gradle.kts").absolutePath

        val newPath = repo.move(source, target)

        assertFalse(File(source).exists())
        assertTrue(File(target, "settings.gradle.kts").exists())
        assertEquals(File(target, "settings.gradle.kts").absolutePath, newPath)
    }

    @Test
    fun `move refuses to place a directory inside itself`() = runBlocking {
        val project = setupProject()
        val app = File(project, "app").absolutePath
        val appMain = File(project, "app/src/main").absolutePath

        val error = runCatching { repo.move(app, appMain) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(File(app).exists())
    }

    @Test
    fun `delete removes a subtree and emits an event`() = runBlocking {
        val project = setupProject()
        val app = File(project, "app").absolutePath

        val event = awaitFirst(repo.observeChanges()) { repo.delete(app) }

        assertEquals(FileChangeType.DELETED, event.type)
        assertFalse(File(app).exists())
    }

    @Test
    fun `listChildren lazily returns a single level`() = runBlocking {
        val project = setupProject()

        val children = repo.listChildren(File(project, "app").absolutePath)

        assertEquals(setOf("src", "build.gradle.kts"), children.map { it.name }.toSet())
        // Lazy: directory children are not eagerly populated.
        assertNull(children.first { it.name == "src" }.children)
    }

    private fun findByName(nodes: List<FileNode>, name: String): FileNode? {
        for (node in nodes) {
            if (node.name == name) return node
            node.children?.let { findByName(it, name)?.let { hit -> return hit } }
        }
        return null
    }
}
