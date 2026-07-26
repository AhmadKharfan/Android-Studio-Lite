package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.model.PullMode
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.api.Git
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Exercises the network-sync surface (push/fetch/pull/tags/publish) against a local
 * bare repository used as `origin`, so the behaviour is verified with no real network.
 */
class JGitGitRepositorySyncTest {

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

    private fun addOrigin(dir: File, origin: File) {
        Git.open(dir).use { git ->
            val config = git.repository.config
            config.setString("remote", "origin", "url", origin.absolutePath)
            config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*")
            config.save()
        }
    }

    private fun bareOrigin(): File {
        val origin = temp.newFolder("origin.git")
        Git.init().setBare(true).setDirectory(origin).call().close()
        return origin
    }

    @Test
    fun `push publishes commits to the origin and sets upstream`() = runTest {
        val repo = repository()
        val origin = bareOrigin()
        val work = temp.newFolder("work")
        repo.init(work)
        repo.commitFile(work, "a.txt", "one\n", "first")
        addOrigin(work, origin)
        val branch = repo.branches(work).single { it.current }.name

        val result = repo.push(work, setUpstreamIfMissing = true)

        assertTrue(result.detail, result.success)
        Git.open(origin).use { git ->
            assertTrue(git.repository.resolve("refs/heads/$branch") != null)
        }
        assertEquals("origin", repo.upstreamOf(work, branch)?.remote)
    }

    @Test
    fun `fetch and pull bring a downstream clone up to date`() = runTest {
        val repo = repository()
        val origin = bareOrigin()
        val work = temp.newFolder("work")
        repo.init(work)
        repo.commitFile(work, "a.txt", "one\n", "first")
        addOrigin(work, origin)
        repo.push(work, setUpstreamIfMissing = true)

        val clone = temp.newFolder("clone")
        Git.cloneRepository().setURI(origin.absolutePath).setDirectory(clone).call().close()

        repo.commitFile(work, "a.txt", "two\n", "second")
        repo.push(work, setUpstreamIfMissing = false)

        val fetched = repo.fetch(clone, remote = "origin", prune = false)
        assertTrue(fetched.detail, fetched.success)

        val pulled = repo.pull(clone, PullMode.MERGE)
        assertTrue(pulled.detail, pulled.success)
        assertEquals("two\n", File(clone, "a.txt").readText())
    }

    @Test
    fun `pushing a tag makes it visible on the origin`() = runTest {
        val repo = repository()
        val origin = bareOrigin()
        val work = temp.newFolder("work")
        repo.init(work)
        repo.commitFile(work, "a.txt", "one\n", "first")
        addOrigin(work, origin)
        repo.push(work, setUpstreamIfMissing = true)
        repo.createTag(work, "v1.0", message = null, targetCommit = null)

        val result = repo.pushTag(work, "v1.0")

        assertTrue(result.detail, result.success)
        Git.open(origin).use { git ->
            assertTrue(git.repository.resolve("refs/tags/v1.0") != null)
        }
    }

    @Test
    fun `publishBranch pushes a new branch to the origin`() = runTest {
        val repo = repository()
        val origin = bareOrigin()
        val work = temp.newFolder("work")
        repo.init(work)
        repo.commitFile(work, "a.txt", "one\n", "first")
        addOrigin(work, origin)
        repo.push(work, setUpstreamIfMissing = true)

        repo.createBranch(work, "feature", checkout = true)
        repo.commitFile(work, "b.txt", "f\n", "feature")

        val result = repo.publishBranch(work, "feature")

        assertTrue(result.detail, result.success)
        Git.open(origin).use { git ->
            assertTrue(git.repository.resolve("refs/heads/feature") != null)
        }
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
