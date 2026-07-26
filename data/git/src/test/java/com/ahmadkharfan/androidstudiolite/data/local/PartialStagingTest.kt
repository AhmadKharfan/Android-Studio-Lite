package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffHunk
import com.ahmadkharfan.androidstudiolite.domain.model.GitException
import com.ahmadkharfan.androidstudiolite.domain.model.GitFastForwardMode
import com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationStatus
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PartialStagingTest {

    @get:Rule val temp = TemporaryFolder()

    private fun repository() = JGitGitRepository(credentialStore = FakeCredentialStore(), io = Dispatchers.Unconfined)

    private fun write(dir: File, name: String, content: String) {
        File(dir, name).apply { parentFile?.mkdirs() }.writeText(content)
    }

    private suspend fun JGitGitRepository.commitFile(dir: File, name: String, content: String, message: String) {
        write(dir, name, content)
        stage(dir, name)
        setCommitMessage(dir, message)
        commit(dir)
    }

    private fun indexContent(dir: File, path: String): String =
        Git.open(dir).use { git ->
            val entry = git.repository.readDirCache().getEntry(path)
            git.repository.open(entry.objectId, Constants.OBJ_BLOB).bytes.toString(Charsets.UTF_8)
        }

    @Test
    fun `stageHunk stages only the requested region and leaves the worktree unchanged`() = runTest {
        val repo = repository()
        val dir = temp.newFolder("stage")
        val base = numberedLines()
        val modified = base.replace("line 2\n", "changed 2\n").replace("line 14\n", "changed 14\n")
        val stagedOnly = base.replace("line 2\n", "changed 2\n")
        repo.init(dir)
        repo.commitFile(dir, "notes.txt", base, "base")
        write(dir, "notes.txt", modified)
        val diff = repo.diffIndexToWorktree(dir, "notes.txt", force = false)
        assertTrue(diff.hunks.size > 1)

        repo.stageHunk(dir, "notes.txt", diff.hunks.first())

        assertEquals(stagedOnly, indexContent(dir, "notes.txt"))
        assertEquals(modified, File(dir, "notes.txt").readText())
    }

    @Test
    fun `stageHunk stages a later hunk rather than the first`() = runTest {
        val repo = repository()
        val dir = temp.newFolder("stage-later")
        val base = numberedLines()
        val modified = base.replace("line 2\n", "changed 2\n").replace("line 14\n", "changed 14\n")
        val stagedOnly = base.replace("line 14\n", "changed 14\n")
        repo.init(dir)
        repo.commitFile(dir, "notes.txt", base, "base")
        write(dir, "notes.txt", modified)
        val diff = repo.diffIndexToWorktree(dir, "notes.txt", force = false)
        assertTrue(diff.hunks.size > 1)

        repo.stageHunk(dir, "notes.txt", diff.hunks.last())

        assertEquals(stagedOnly, indexContent(dir, "notes.txt"))
        assertEquals(modified, File(dir, "notes.txt").readText())
    }

    @Test
    fun `unstageHunk restores only the requested index region`() = runTest {
        val repo = repository()
        val dir = temp.newFolder("unstage")
        val base = numberedLines()
        val modified = base.replace("line 2\n", "changed 2\n").replace("line 14\n", "changed 14\n")
        repo.init(dir)
        repo.commitFile(dir, "notes.txt", base, "base")
        write(dir, "notes.txt", modified)
        repo.stageHunk(dir, "notes.txt", repo.diffIndexToWorktree(dir, "notes.txt", force = false).hunks.first())
        val stagedDiff = repo.diffHeadToIndex(dir, "notes.txt", force = false)

        repo.unstageHunk(dir, "notes.txt", stagedDiff.hunks.single())

        assertEquals(base, indexContent(dir, "notes.txt"))
        assertEquals(modified, File(dir, "notes.txt").readText())
    }

    @Test
    fun `stageHunk rejects a hunk that no longer matches the current diff`() = runTest {
        val repo = repository()
        val dir = temp.newFolder("stale")
        val base = numberedLines()
        repo.init(dir)
        repo.commitFile(dir, "notes.txt", base, "base")
        write(dir, "notes.txt", base.replace("line 2\n", "changed 2\n"))
        val current = repo.diffIndexToWorktree(dir, "notes.txt", force = false).hunks.single()
        val stale = current.copy(oldStart = current.oldStart + 100, newStart = current.newStart + 100)

        val error = runCatching { repo.stageHunk(dir, "notes.txt", stale) }.exceptionOrNull()

        assertEquals(GitException.PartialStaging::class.java, error?.javaClass)
        assertEquals("The file changed; refresh the diff and try again", error?.message)
    }

    @Test
    fun `stageHunk rejects a file with conflict stages`() = runTest {
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

        val error = runCatching {
            repo.stageHunk(dir, "conflict.txt", GitDiffHunk(1, 1, 1, 1, emptyList()))
        }.exceptionOrNull()

        assertEquals(GitException.PartialStaging::class.java, error?.javaClass)
        assertEquals("Resolve conflicts before staging individual hunks", error?.message)
    }

    private fun numberedLines(): String = (1..18).joinToString(separator = "\n", postfix = "\n") { "line $it" }

    private class FakeCredentialStore : GitCredentialStore {
        override fun credentialsForUrl(url: String): GitCredentials? = null
        override fun credentialsForHost(host: String): GitCredentials? = null
        override fun hasCredentials(host: String): Boolean = false
        override fun save(host: String, credentials: GitCredentials) = Unit
        override fun clear(host: String) = Unit
        override val changes = kotlinx.coroutines.flow.emptyFlow<Unit>()
    }
}
