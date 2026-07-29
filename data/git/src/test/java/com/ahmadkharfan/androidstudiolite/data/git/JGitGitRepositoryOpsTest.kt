package com.ahmadkharfan.androidstudiolite.data.git

import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitResetMode
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JGitGitRepositoryOpsTest {

    @get:Rule val temp = TemporaryFolder()

    private fun repository() = JGitGitRepository(credentialStore = FakeCredentialStore(), io = Dispatchers.Unconfined)

    private fun write(dir: File, name: String, content: String) {
        File(dir, name).apply { parentFile?.mkdirs() }.writeText(content)
    }

    private suspend fun JGitGitRepository.commitFile(dir: File, name: String, content: String, message: String): String {
        write(dir, name, content)
        stage(dir, name)
        setCommitMessage(dir, message)
        return commit(dir)
    }

    @Test
    fun `tags can be created listed and deleted`() = runTest {
        val repo = repository()
        val dir = temp.newFolder("work")
        repo.init(dir)
        repo.commitFile(dir, "a.txt", "x\n", "base")

        repo.createTag(dir, "v1.0", message = null, targetCommit = null)
        assertTrue(repo.listTags(dir).any { it.name == "v1.0" })

        repo.deleteTag(dir, "v1.0")
        assertTrue(repo.listTags(dir).none { it.name == "v1.0" })
    }

    @Test
    fun `hard reset moves HEAD back to an earlier commit`() = runTest {
        val repo = repository()
        val dir = temp.newFolder("work")
        repo.init(dir)
        repo.commitFile(dir, "a.txt", "one\n", "first")
        repo.commitFile(dir, "a.txt", "two\n", "second")
        val firstId = repo.log(dir, 10).last().id

        repo.reset(dir, firstId, GitResetMode.HARD)

        assertEquals(listOf("first"), repo.log(dir, 10).map { it.message })
        assertEquals("one\n", File(dir, "a.txt").readText())
    }

    @Test
    fun `merging a fast-forward branch brings its file into the worktree`() = runTest {
        val repo = repository()
        val dir = temp.newFolder("work")
        repo.init(dir)
        repo.commitFile(dir, "base.txt", "b\n", "base")
        val mainBranch = repo.branches(dir).single { it.current }.name

        repo.createBranch(dir, "feature", checkout = true)
        repo.commitFile(dir, "feature.txt", "f\n", "feature work")
        repo.checkout(dir, mainBranch)

        val result = repo.merge(dir, "feature", com.ahmadkharfan.androidstudiolite.domain.model.GitFastForwardMode.FF_ALLOWED, null)

        assertTrue(result.status.name, result.status != GitIntegrationStatus.CONFLICTS)
        assertTrue(File(dir, "feature.txt").exists())
    }

    @Test
    fun `a conflicting merge is reported and can be resolved`() = runTest {
        val repo = repository()
        val dir = temp.newFolder("work")
        repo.init(dir)
        repo.commitFile(dir, "c.txt", "base\n", "base")
        val mainBranch = repo.branches(dir).single { it.current }.name

        repo.createBranch(dir, "other", checkout = true)
        repo.commitFile(dir, "c.txt", "theirs\n", "theirs")
        repo.checkout(dir, mainBranch)
        repo.commitFile(dir, "c.txt", "ours\n", "ours")

        val result = repo.merge(dir, "other", com.ahmadkharfan.androidstudiolite.domain.model.GitFastForwardMode.FF_ALLOWED, null)
        assertEquals(GitIntegrationStatus.CONFLICTS, result.status)
        assertTrue(repo.conflictEntries(dir).any { it.path == "c.txt" })

        repo.resolveAcceptOurs(dir, "c.txt")
        repo.markResolved(dir, "c.txt")
        assertTrue(repo.conflictEntries(dir).isEmpty())
        assertEquals("ours\n", File(dir, "c.txt").readText())
    }

    @Test
    fun `clean removes untracked files`() = runTest {
        val repo = repository()
        val dir = temp.newFolder("work")
        repo.init(dir)
        repo.commitFile(dir, "tracked.txt", "t\n", "base")
        write(dir, "junk.txt", "junk\n")

        val removed = repo.clean(dir, dryRun = false)

        assertTrue(removed.contains("junk.txt"))
        assertFalse(File(dir, "junk.txt").exists())
    }

    @Test
    fun `restoreFiles reverts a tracked file to HEAD`() = runTest {
        val repo = repository()
        val dir = temp.newFolder("work")
        repo.init(dir)
        repo.commitFile(dir, "a.txt", "committed\n", "base")
        write(dir, "a.txt", "local edit\n")

        repo.restoreFiles(dir, listOf("a.txt"))

        assertEquals("committed\n", File(dir, "a.txt").readText())
    }

    @Test
    fun `a repository without submodules reports none and init is a no-op`() = runTest {
        val repo = repository()
        val dir = temp.newFolder("work")
        repo.init(dir)
        repo.commitFile(dir, "a.txt", "x\n", "base")

        assertTrue(repo.submodules(dir).isEmpty())
        repo.submoduleInit(dir)
        assertTrue(repo.submodules(dir).isEmpty())
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
