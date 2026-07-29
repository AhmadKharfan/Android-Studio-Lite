package com.ahmadkharfan.androidstudiolite.data.git

import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.model.GitException
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JGitForceWithLeaseTest {

    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `force with lease pushes when the tracking ref matches`() = runTest {
        val repo = repository()
        val origin = bareOrigin()
        val work = initializedRepository(repo, origin)
        val branch = repo.branches(work).single { it.current }.name
        repo.push(work, setUpstreamIfMissing = true)
        repo.fetch(work, remote = "origin", prune = false)
        commitFile(repo, work, "two\n", "second")

        val result = repo.pushForceWithLease(work)

        assertEquals("Pushed 1 ref(s)", result.detail)
        val localHead = Git.open(work).use { it.repository.resolve(Constants.HEAD).name }
        Git.open(origin).use { git ->
            assertEquals(localHead, git.repository.resolve("refs/heads/$branch").name)
        }
    }

    @Test
    fun `force with lease requires a remote tracking value`() = runTest {
        val repo = repository()
        val origin = bareOrigin()
        val work = initializedRepository(repo, origin)
        val branch = repo.branches(work).single { it.current }.name
        repo.setUpstream(work, branch, "origin", branch)

        try {
            repo.pushForceWithLease(work)
            fail("Expected a stale lease failure")
        } catch (error: GitException.StaleLease) {
            assertEquals("No remote-tracking value for origin/$branch; fetch first", error.message)
        }
    }

    private fun repository() =
        JGitGitRepository(credentialStore = FakeCredentialStore(), io = Dispatchers.Unconfined)

    private fun bareOrigin(): File = temp.newFolder("origin.git").also { origin ->
        Git.init().setBare(true).setDirectory(origin).call().close()
    }

    private suspend fun initializedRepository(repo: JGitGitRepository, origin: File): File =
        temp.newFolder("work").also { work ->
            repo.init(work)
            commitFile(repo, work, "one\n", "first")
            Git.open(work).use { git ->
                git.remoteAdd().setName("origin").setUri(org.eclipse.jgit.transport.URIish(origin.absolutePath)).call()
            }
        }

    private suspend fun commitFile(repo: JGitGitRepository, work: File, content: String, message: String) {
        File(work, "a.txt").writeText(content)
        repo.stage(work, "a.txt")
        repo.setCommitMessage(work, message)
        repo.commit(work)
    }

    private class FakeCredentialStore : GitCredentialStore {
        override fun credentialsForUrl(url: String): GitCredentials? = null
        override fun credentialsForHost(host: String): GitCredentials? = null
        override fun hasCredentials(host: String): Boolean = false
        override fun save(host: String, credentials: GitCredentials) = Unit
        override fun clear(host: String) = Unit
        override val changes = emptyFlow<Unit>()
    }
}
