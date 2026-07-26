package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.model.GitConflictStage
import com.ahmadkharfan.androidstudiolite.domain.model.GitFastForwardMode
import com.ahmadkharfan.androidstudiolite.domain.model.GitIndexStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitState
import com.ahmadkharfan.androidstudiolite.domain.model.GitWorktreeStatus
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.lib.NullProgressMonitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JGitStatusComputerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun repository() =
        JGitGitRepository(credentialStore = FakeCredentialStore(), io = Dispatchers.Unconfined)

    private fun write(dir: File, name: String, content: String) {
        File(dir, name).apply { parentFile?.mkdirs() }.writeText(content)
    }

    private fun compute(
        dir: File,
        includeIgnored: Boolean = false,
        commitMessage: String = "",
    ): GitState = JGitStatusComputer().compute(
        repoDir = dir,
        includeIgnored = includeIgnored,
        commitMessage = commitMessage,
        monitor = NullProgressMonitor.INSTANCE,
    )

    private suspend fun JGitGitRepository.commitFile(
        dir: File,
        name: String,
        content: String,
        message: String,
    ): String {
        write(dir, name, content)
        stage(dir, name)
        setCommitMessage(dir, message)
        return commit(dir)
    }

    @Test
    fun `plain directory is not a repository and preserves commit message`() {
        val state = compute(temp.newFolder("plain"), commitMessage = "draft")

        assertFalse(state.isRepository)
        assertTrue(state.files.isEmpty())
        assertEquals("draft", state.commitMessage)
    }

    @Test
    fun `untracked file has only untracked worktree status`() = runTest {
        val repo = repository()
        val dir = temp.newFolder("untracked")
        repo.init(dir)
        write(dir, "new.txt", "new\n")

        val file = compute(dir).files.single()

        assertEquals("new.txt", file.path)
        assertEquals(GitIndexStatus.UNCHANGED, file.indexStatus)
        assertEquals(GitWorktreeStatus.UNTRACKED, file.worktreeStatus)
    }

    @Test
    fun `staged new file has added index status`() = runTest {
        val repo = repository()
        val dir = temp.newFolder("added")
        repo.init(dir)
        write(dir, "new.txt", "new\n")
        repo.stage(dir, "new.txt")

        val file = compute(dir).files.single()

        assertEquals("new.txt", file.path)
        assertEquals(GitIndexStatus.ADDED, file.indexStatus)
        assertEquals(GitWorktreeStatus.UNCHANGED, file.worktreeStatus)
    }

    @Test
    fun `staged modifications and deletions have their index statuses`() = runTest {
        val repo = repository()
        val dir = temp.newFolder("staged-changes")
        repo.init(dir)
        repo.commitFile(dir, "modified.txt", "base\n", "base")
        repo.commitFile(dir, "deleted.txt", "base\n", "add deleted file")
        write(dir, "modified.txt", "staged\n")
        File(dir, "deleted.txt").delete()
        repo.stage(dir, "modified.txt")
        repo.stage(dir, "deleted.txt")

        val files = compute(dir).files.associateBy { it.path }

        assertEquals(GitIndexStatus.MODIFIED, files.getValue("modified.txt").indexStatus)
        assertEquals(GitIndexStatus.DELETED, files.getValue("deleted.txt").indexStatus)
    }

    @Test
    fun `unstaged modification has modified worktree status`() = runTest {
        val repo = repository()
        val dir = temp.newFolder("modified")
        repo.init(dir)
        repo.commitFile(dir, "tracked.txt", "base\n", "base")
        write(dir, "tracked.txt", "changed\n")

        val file = compute(dir).files.single()

        assertEquals(GitIndexStatus.UNCHANGED, file.indexStatus)
        assertEquals(GitWorktreeStatus.MODIFIED, file.worktreeStatus)
    }

    @Test
    fun `missing indexed file has deleted worktree status`() = runTest {
        val repo = repository()
        val dir = temp.newFolder("missing")
        repo.init(dir)
        repo.commitFile(dir, "tracked.txt", "base\n", "base")
        File(dir, "tracked.txt").delete()

        val file = compute(dir).files.single()

        assertEquals(GitIndexStatus.UNCHANGED, file.indexStatus)
        assertEquals(GitWorktreeStatus.DELETED, file.worktreeStatus)
    }

    @Test
    fun `staged file modified again carries index and worktree statuses`() = runTest {
        val repo = repository()
        val dir = temp.newFolder("staged-and-modified")
        repo.init(dir)
        repo.commitFile(dir, "tracked.txt", "base\n", "base")
        write(dir, "tracked.txt", "staged\n")
        repo.stage(dir, "tracked.txt")
        write(dir, "tracked.txt", "worktree\n")

        val file = compute(dir).files.single()

        assertEquals(GitIndexStatus.MODIFIED, file.indexStatus)
        assertEquals(GitWorktreeStatus.MODIFIED, file.worktreeStatus)
    }

    @Test
    fun `conflicting merge reports the file as conflicted`() = runTest {
        val repo = repository()
        val dir = temp.newFolder("conflict")
        repo.init(dir)
        repo.commitFile(dir, "conflict.txt", "base\n", "base")
        val mainBranch = repo.branches(dir).single { it.current }.name

        repo.createBranch(dir, "other", checkout = true)
        repo.commitFile(dir, "conflict.txt", "theirs\n", "theirs")
        repo.checkout(dir, mainBranch)
        repo.commitFile(dir, "conflict.txt", "ours\n", "ours")

        val merge = repo.merge(dir, "other", GitFastForwardMode.FF_ALLOWED, null)
        assertEquals(GitIntegrationStatus.CONFLICTS, merge.status)

        val file = compute(dir).files.single { it.path == "conflict.txt" }

        assertNotNull(file.conflictStage)
        assertEquals(
            setOf(GitConflictStage.BASE, GitConflictStage.OURS, GitConflictStage.THEIRS),
            file.conflictStage!!.stages,
        )
    }

    @Test
    fun `ignored file appears only when ignored files are included`() = runTest {
        val repo = repository()
        val dir = temp.newFolder("ignored")
        repo.init(dir)
        repo.commitFile(dir, ".gitignore", "ignored.txt\n", "ignore file")
        write(dir, "ignored.txt", "ignored\n")

        assertTrue(compute(dir, includeIgnored = false).files.isEmpty())

        val file = compute(dir, includeIgnored = true).files.single()

        assertEquals("ignored.txt", file.path)
        assertEquals(GitIndexStatus.UNCHANGED, file.indexStatus)
        assertEquals(GitWorktreeStatus.IGNORED, file.worktreeStatus)
    }

    private class FakeCredentialStore : GitCredentialStore {
        override fun credentialsForUrl(url: String): GitCredentials? = null
        override fun credentialsForHost(host: String): GitCredentials? = null
        override fun hasCredentials(host: String): Boolean = false
        override fun save(host: String, credentials: GitCredentials) = Unit
        override fun clear(host: String) = Unit
        override val changes = kotlinx.coroutines.flow.emptyFlow<Unit>()
    }
}
