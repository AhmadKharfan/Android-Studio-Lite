package com.ahmadkharfan.androidstudiolite.data.ai.agent

import com.ahmadkharfan.androidstudiolite.data.gradle.GradleProjectReader
import com.ahmadkharfan.androidstudiolite.domain.model.AgentAction
import com.ahmadkharfan.androidstudiolite.domain.model.AgentToolResult
import com.ahmadkharfan.androidstudiolite.domain.model.FileNode
import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.Project
import com.ahmadkharfan.androidstudiolite.domain.repository.FileContentRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.FileTreeRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import com.ahmadkharfan.androidstudiolite.domain.usecase.ProjectPathResolver
import java.io.File
import java.lang.reflect.InvocationTargetException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AgentToolExecutorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var projectRoot: File
    private lateinit var executor: AgentToolExecutor

    @Before
    fun setUp() {
        projectRoot = temporaryFolder.newFolder("project")
        executor = AgentToolExecutor(
            fileContentRepository = FakeFileContentRepository(),
            fileTreeRepository = FakeFileTreeRepository(),
            projectPathResolver = ProjectPathResolver(FakeProjectRepository(projectRoot)),
            gradleProjectReader = GradleProjectReader(),
        )
    }

    @Test
    fun `every agent action type has a registered handler`() = runBlocking {
        val actions = listOf(
            AgentAction.ListDir("."),
            AgentAction.ReadFile("a.txt"),
            AgentAction.Search("query"),
            AgentAction.CreateFile("new.txt", "content"),
            AgentAction.CreateDir("dir"),
            AgentAction.EditFile("a.txt", "content"),
            AgentAction.Rename("a.txt", "b.txt"),
            AgentAction.Move("a.txt", "dir"),
            AgentAction.Delete("a.txt"),
        )

        actions.forEach { action ->
            val output = executor.run(PROJECT_ID, action).output
            assertFalse("$action has no handler: $output", output.contains("is missing in the map"))
        }
    }

    @Test
    fun `resolve rejects paths that canonicalize outside the project`() {
        assertResolveFailure("../outside.txt", "Path escapes the project: ../outside.txt")
        assertResolveFailure("/../../etc/passwd", "Path escapes the project: /../../etc/passwd")
    }

    @Test
    fun `resolve re-roots absolute paths under the project`() {
        assertEquals(
            File(projectRoot, "tmp/outside.txt").canonicalFile,
            resolve("/tmp/outside.txt"),
        )
    }

    @Test
    fun `resolve rejects paths into git metadata`() {
        assertResolveFailure(
            ".git/config",
            "The .git directory is off-limits: .git/config",
        )
    }

    @Test
    fun `resolve normalizes ordinary and root paths`() {
        val expected = File(projectRoot, "src/Main.kt").canonicalFile
        assertEquals(expected, resolve("src/Main.kt"))
        assertEquals(expected, resolve("./src/Main.kt"))
        assertEquals(projectRoot.canonicalFile, resolve(""))
        assertEquals(projectRoot.canonicalFile, resolve("."))
    }

    @Test
    fun `list directory returns children and empty marker`() = runBlocking {
        File(projectRoot, "docs/empty").mkdirs()
        File(projectRoot, "docs/readme.txt").writeText("read me")

        val populated = runTool(AgentAction.ListDir("docs"))
        val empty = runTool(AgentAction.ListDir("docs/empty"))

        assertSuccess(populated, "docs/empty/\ndocs/readme.txt")
        assertSuccess(empty, "(empty)")
    }

    @Test
    fun `read file returns fenced content`() = runBlocking {
        File(projectRoot, "notes.txt").writeText("hello")

        val toolResult = runTool(AgentAction.ReadFile("notes.txt"))

        assertSuccess(toolResult, "```\nhello\n```")
    }

    @Test
    fun `create file writes content and rejects an existing path`() = runBlocking {
        val created = runTool(AgentAction.CreateFile("new.txt", "first"))
        val rejected = runTool(AgentAction.CreateFile("new.txt", "second"))

        assertSuccess(created, "Created new.txt")
        assertEquals("first", File(projectRoot, "new.txt").readText())
        assertFalse(rejected.ok)
        assertEquals(
            "Already exists: new.txt (use edit_file to overwrite)",
            rejected.output,
        )
    }

    @Test
    fun `edit file replaces content`() = runBlocking {
        val file = File(projectRoot, "editable.txt").apply { writeText("before") }

        val toolResult = runTool(AgentAction.EditFile("editable.txt", "after"))

        assertSuccess(toolResult, "Updated editable.txt")
        assertEquals("after", file.readText())
    }

    @Test
    fun `delete removes the requested file`() = runBlocking {
        val file = File(projectRoot, "obsolete.txt").apply { writeText("old") }

        val toolResult = runTool(AgentAction.Delete("obsolete.txt"))

        assertSuccess(toolResult, "Deleted obsolete.txt")
        assertFalse(file.exists())
    }

    @Test
    fun `move relocates the requested file`() = runBlocking {
        File(projectRoot, "source.txt").writeText("content")
        File(projectRoot, "destination").mkdir()

        val toolResult = runTool(AgentAction.Move("source.txt", "destination"))

        assertSuccess(toolResult, "Moved to destination/source.txt")
        assertFalse(File(projectRoot, "source.txt").exists())
        assertEquals("content", File(projectRoot, "destination/source.txt").readText())
    }

    @Test
    fun `search returns matching line and no-match text`() = runBlocking {
        File(projectRoot, "notes.txt").writeText("First line\nFind Needle here\n")

        val matched = runTool(AgentAction.Search("needle"))
        val missing = runTool(AgentAction.Search("absent"))

        assertSuccess(matched, "notes.txt:2: Find Needle here")
        assertSuccess(missing, "No matches for \"absent\"")
    }

    private suspend fun runTool(action: AgentAction): AgentToolResult =
        executor.run(PROJECT_ID, action)

    private fun assertSuccess(toolResult: AgentToolResult, expectedOutput: String) {
        assertTrue(toolResult.ok)
        assertEquals(expectedOutput, toolResult.output)
    }

    private fun assertResolveFailure(path: String, expectedMessage: String) {
        try {
            resolve(path)
            fail("Expected resolve to reject $path")
        } catch (error: IllegalArgumentException) {
            assertEquals(expectedMessage, error.message)
        }
    }

    private fun resolve(path: String): File {
        val method = AgentToolExecutor::class.java.getDeclaredMethod(
            "resolve",
            File::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return try {
            method.invoke(executor, projectRoot, path) as File
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }

    private class FakeFileContentRepository : FileContentRepository {
        override suspend fun readText(path: String): String = File(path).readText()

        override suspend fun writeText(path: String, text: String) {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(text)
        }
    }

    private class FakeFileTreeRepository : FileTreeRepository {
        override suspend fun getFileTree(projectId: String): List<FileNode> = emptyList()

        override suspend fun listChildren(path: String): List<FileNode> =
            File(path).listFiles().orEmpty()
                .sortedBy(File::getName)
                .map { file ->
                    FileNode(
                        id = file.absolutePath,
                        name = file.name,
                        children = if (file.isDirectory) emptyList() else null,
                    )
                }

        override suspend fun delete(path: String) {
            check(File(path).delete())
        }

        override suspend fun move(path: String, newParentPath: String): String {
            val source = File(path)
            val destination = File(newParentPath, source.name)
            check(source.renameTo(destination))
            return destination.absolutePath
        }
    }

    private class FakeProjectRepository(root: File) : ProjectRepository {
        private val project = Project(
            id = PROJECT_ID,
            name = "Project",
            path = root.absolutePath,
            language = "Kotlin",
            lastOpenedMillis = null,
        )

        override fun observeRecentProjects(): Flow<List<Project>> = flowOf(listOf(project))
        override suspend fun createProject(spec: NewProjectSpec): Project = error("unused")
        override suspend fun registerExistingProject(path: File): Project = error("unused")
        override suspend fun openProject(id: String): Project = project
        override suspend fun deleteProject(id: String) = Unit
        override suspend fun renameProject(id: String, newName: String) = Unit
    }

    private companion object {
        const val PROJECT_ID = "project"
    }
}
