package com.ahmadkharfan.androidstudiolite.feature.editor

import com.ahmadkharfan.androidstudiolite.domain.model.FileNode
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileState
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitWorktreeStatus
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EditorFileTreePresentationTest {

    @get:Rule val temp = TemporaryFolder()

    private fun modified(path: String) =
        GitFileState(path = path, worktreeStatus = GitWorktreeStatus.MODIFIED)

    @Test
    fun layeredGitStatusResolvesPathRelativeToProjectRoot() {
        val root = temp.newFolder("project")
        val file = File(root, "src/Main.kt")
        val gitFiles = mapOf("src/Main.kt" to modified("src/Main.kt"))

        assertEquals(GitFileStatus.MODIFIED, layeredGitStatus(file.path, root, gitFiles))
    }

    @Test
    fun layeredGitStatusIsNullForUntrackedFileOrMissingRoot() {
        val root = temp.newFolder("project")
        val file = File(root, "src/Untracked.kt")

        assertNull(layeredGitStatus(file.path, root, emptyMap()))
        assertNull(layeredGitStatus(file.path, projectRoot = null, gitFiles = emptyMap()))
    }

    @Test
    fun toEditorNodeLayersGitStatusOnFilesAndLeavesFoldersUnmarked() {
        val root = temp.newFolder("project")
        val filePath = File(root, "A.kt").path
        val tree = FileNode(
            id = File(root, "app").path,
            name = "app",
            children = listOf(FileNode(id = filePath, name = "A.kt")),
        )
        val gitFiles = mapOf("A.kt" to modified("A.kt"))

        val node = tree.toEditorNode(root.path, gitFiles)
        assertNull(node.gitStatus)
        assertNull(node.icon)
        assertEquals(GitFileStatus.MODIFIED, node.children!!.single().gitStatus)
    }
}
