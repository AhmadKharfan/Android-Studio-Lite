package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileStatus
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.api.Git
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Integration tests for [JGitGitRepository] against real on-disk repositories and local `file://`
 * remotes — no network. The repo is constructed with an unconfined dispatcher so `withContext(io)`
 * runs inline under [runTest].
 */
class JGitGitRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val credentialStore = FakeCredentialStore()

    private fun repository(projectsHome: File = temp.newFolder("projects")): JGitGitRepository =
        JGitGitRepository(
            projectsHome = { projectsHome },
            credentialStore = credentialStore,
            io = Dispatchers.Unconfined,
        )

    private fun writeFile(dir: File, name: String, content: String) {
        File(dir, name).apply { parentFile?.mkdirs() }.writeText(content)
    }

    @Test
    fun `init commit and log`() = runTest {
        val repo = repository()
        val dir = temp.newFolder("work")
        repo.init(dir)
        assertTrue(repo.isRepository(dir))

        writeFile(dir, "README.md", "hello\n")
        repo.stage(dir, "README.md")
        repo.setCommitMessage(dir, "initial commit")
        val id = repo.commit(dir)

        assertTrue(id.isNotBlank())
        val log = repo.log(dir, 10)
        assertEquals(1, log.size)
        assertEquals("initial commit", log.first().message)
        // Working tree is clean after committing everything.
        repo.refresh(dir)
        assertTrue(repo.observeState(dir).value.changes.isEmpty())
    }

    @Test
    fun `staging moves a change from unstaged to staged`() = runTest {
        val repo = repository()
        val dir = temp.newFolder("work")
        repo.init(dir)

        writeFile(dir, "a.txt", "one\n")
        repo.refresh(dir)
        val untracked = repo.observeState(dir).value.changes.single()
        assertEquals("a.txt", untracked.path)
        assertEquals(GitFileStatus.UNTRACKED, untracked.status)
        assertFalse(untracked.staged)

        repo.stage(dir, "a.txt")
        val staged = repo.observeState(dir).value.changes.single()
        assertTrue(staged.staged)
        assertEquals(GitFileStatus.ADDED, staged.status)

        repo.unstage(dir, "a.txt")
        assertFalse(repo.observeState(dir).value.changes.single().staged)
    }

    @Test
    fun `diff reports added and modified lines`() = runTest {
        val repo = repository()
        val dir = temp.newFolder("work")
        repo.init(dir)
        writeFile(dir, "code.txt", "line1\nline2\n")
        repo.stage(dir, "code.txt")
        repo.setCommitMessage(dir, "base")
        repo.commit(dir)

        writeFile(dir, "code.txt", "line1\nline2 changed\nline3\n")
        val diff = repo.getDiff(dir, "code.txt")

        assertTrue(diff.any { it.kind.name == "REMOVED" && it.text == "line2" })
        assertTrue(diff.any { it.kind.name == "ADDED" && it.text == "line2 changed" })
        assertTrue(diff.any { it.kind.name == "ADDED" && it.text == "line3" })
    }

    @Test
    fun `create checkout and list branches`() = runTest {
        val repo = repository()
        val dir = temp.newFolder("work")
        repo.init(dir)
        writeFile(dir, "f.txt", "x\n")
        repo.stage(dir, "f.txt")
        repo.setCommitMessage(dir, "base")
        repo.commit(dir)

        repo.createBranch(dir, "feature/foo", checkout = true)
        val branches = repo.branches(dir)
        val current = branches.single { it.current }
        assertEquals("feature/foo", current.name)
        assertTrue(branches.any { it.name == "feature/foo" })
    }

    @Test
    fun `clone from a local remote creates a project`() = runTest {
        val source = seedRepo(temp.newFolder("sample"))
        val projectsHome = temp.newFolder("projects")
        val repo = repository(projectsHome)

        val progress = repo.clone(source.toURI().toString(), branch = null, credentials = null).toList()

        val last = progress.last()
        assertEquals("sample", last.clonedProjectId)
        assertEquals(1f, last.fraction)
        val cloned = File(projectsHome, "sample")
        assertTrue(File(cloned, "README.md").exists())
        assertTrue(repo.isRepository(cloned))
    }

    @Test
    fun `push and pull round trip through a bare remote`() = runTest {
        val bare = temp.newFolder("remote.git")
        Git.init().setBare(true).setDirectory(bare).call().close()

        val projectsHome = temp.newFolder("projects")
        val repo = repository(projectsHome)

        // Clone A: add a commit and push it to the bare remote.
        repo.clone(bare.toURI().toString(), branch = null, credentials = null).toList()
        val cloneA = File(projectsHome, "remote")
        writeFile(cloneA, "hello.txt", "from A\n")
        repo.stage(cloneA, "hello.txt")
        repo.setCommitMessage(cloneA, "add hello")
        repo.commit(cloneA)
        val push = repo.push(cloneA)
        assertTrue(push.detail, push.success)

        // Clone B (into a separate home) should see A's commit.
        val homeB = temp.newFolder("projectsB")
        val repoB = repository(homeB)
        repoB.clone(bare.toURI().toString(), branch = null, credentials = null).toList()
        val cloneB = File(homeB, "remote")
        assertTrue(File(cloneB, "hello.txt").exists())
        assertEquals("add hello", repoB.log(cloneB, 5).first().message)

        // A second commit pushed from A is retrieved by B via pull.
        writeFile(cloneA, "hello.txt", "from A again\n")
        repo.stage(cloneA, "hello.txt")
        repo.setCommitMessage(cloneA, "update hello")
        repo.commit(cloneA)
        repo.push(cloneA)

        val pull = repoB.pull(cloneB)
        assertTrue(pull.detail, pull.success)
        assertEquals("from A again\n", File(cloneB, "hello.txt").readText())
    }

    @Test
    fun `observeState reports non-repository directories gracefully`() = runTest {
        val repo = repository()
        val plain = temp.newFolder("not-a-repo")
        assertFalse(repo.isRepository(plain))
        assertFalse(repo.observeState(plain).value.isRepository)
    }

    /** Create a non-bare repo with one committed file, usable as a clone source. */
    private fun seedRepo(dir: File): File {
        Git.init().setDirectory(dir).call().use { git ->
            File(dir, "README.md").writeText("hello\n")
            git.add().addFilepattern("README.md").call()
            git.commit().setMessage("seed").setAuthor("t", "t@t").setCommitter("t", "t@t").call()
        }
        return dir
    }

    private class FakeCredentialStore : GitCredentialStore {
        private val byHost = mutableMapOf<String, GitCredentials>()
        override fun credentialsForUrl(url: String): GitCredentials? =
            runCatching { java.net.URI(url).host }.getOrNull()?.let { byHost[it] }
        override fun save(host: String, credentials: GitCredentials) { byHost[host] = credentials }
        override fun clear(host: String) { byHost.remove(host) }
    }
}
