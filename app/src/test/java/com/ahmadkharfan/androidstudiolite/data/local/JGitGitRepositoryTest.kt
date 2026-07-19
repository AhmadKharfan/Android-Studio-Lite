package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.CloneOptions
import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffKind
import com.ahmadkharfan.androidstudiolite.domain.model.GitIndexStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitWorktreeStatus
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.api.Git
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    private fun repository(): JGitGitRepository =
        JGitGitRepository(
            credentialStore = credentialStore,
            io = Dispatchers.Unconfined,
        )

    /** A path under a fresh folder that does not yet exist — a valid clone destination. */
    private fun cloneDestination(name: String): File = File(temp.newFolder(), name)

    private fun writeFile(dir: File, name: String, content: String) {
        File(dir, name).apply { parentFile?.mkdirs() }.writeText(content)
    }

    /** Files with a real staged/unstaged change (ignores UNCHANGED entries). */
    private suspend fun JGitGitRepository.pendingFiles(dir: File) =
        observeState(dir).value.files.filter { it.hasPendingChange }

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
        assertTrue(repo.pendingFiles(dir).isEmpty())
    }

    @Test
    fun `staging moves a change from unstaged to staged`() = runTest {
        val repo = repository()
        val dir = temp.newFolder("work")
        repo.init(dir)

        writeFile(dir, "a.txt", "one\n")
        repo.refresh(dir)
        val untracked = repo.pendingFiles(dir).single()
        assertEquals("a.txt", untracked.path)
        assertEquals(GitWorktreeStatus.UNTRACKED, untracked.worktreeStatus)
        // Not staged yet: nothing in the index for this path.
        assertEquals(GitIndexStatus.UNCHANGED, untracked.indexStatus)

        repo.stage(dir, "a.txt")
        repo.refresh(dir)
        val staged = repo.pendingFiles(dir).single()
        assertEquals(GitIndexStatus.ADDED, staged.indexStatus)

        repo.unstage(dir, "a.txt")
        repo.refresh(dir)
        assertEquals(GitIndexStatus.UNCHANGED, repo.pendingFiles(dir).single().indexStatus)
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
        val diff = repo.diffIndexToWorktree(dir, "code.txt")
        val lines = diff.hunks.flatMap { it.lines }

        assertTrue(lines.any { it.kind == GitDiffKind.REMOVED && it.text == "line2" })
        assertTrue(lines.any { it.kind == GitDiffKind.ADDED && it.text == "line2 changed" })
        assertTrue(lines.any { it.kind == GitDiffKind.ADDED && it.text == "line3" })
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
    fun `clone from a local remote populates the destination`() = runTest {
        val source = seedRepo(temp.newFolder("sample"))
        val repo = repository()
        val dest = cloneDestination("sample")

        repo.clone(source.toURI().toString(), dest, CloneOptions(), credentials = null).toList()

        assertTrue(File(dest, "README.md").exists())
        assertTrue(repo.isRepository(dest))
    }

    @Test
    fun `push and pull round trip through a bare remote`() = runTest {
        val bare = temp.newFolder("remote.git")
        Git.init().setBare(true).setDirectory(bare).call().close()
        val repo = repository()

        // Clone A: add a commit and push it to the bare remote.
        val cloneA = cloneDestination("remoteA")
        repo.clone(bare.toURI().toString(), cloneA, CloneOptions(), credentials = null).toList()
        writeFile(cloneA, "hello.txt", "from A\n")
        repo.stage(cloneA, "hello.txt")
        repo.setCommitMessage(cloneA, "add hello")
        repo.commit(cloneA)
        val push = repo.push(cloneA)
        assertTrue(push.detail, push.success)

        // Clone B should see A's commit.
        val cloneB = cloneDestination("remoteB")
        repo.clone(bare.toURI().toString(), cloneB, CloneOptions(), credentials = null).toList()
        assertTrue(File(cloneB, "hello.txt").exists())
        assertEquals("add hello", repo.log(cloneB, 5).first().message)

        // A second commit pushed from A is retrieved by B via pull.
        writeFile(cloneA, "hello.txt", "from A again\n")
        repo.stage(cloneA, "hello.txt")
        repo.setCommitMessage(cloneA, "update hello")
        repo.commit(cloneA)
        repo.push(cloneA)

        val pull = repo.pull(cloneB)
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

    @Test
    fun `remoteInfo returns the origin url and current branch for a clone`() = runTest {
        val repo = repository()
        val source = seedRepo(temp.newFolder("origin"))
        val cloned = cloneDestination("origin-clone")

        repo.clone(source.toURI().toString(), cloned, CloneOptions(), credentials = null).toList()

        val info = repo.remoteInfo(cloned)
        assertNotNull(info)
        // JGit stores the origin URL it cloned from; it points at the seeded source repo.
        assertTrue(info!!.url, info.url.contains("origin"))
        // The default branch checked out by the clone (master/main depending on JGit defaults).
        assertTrue(info.ref.isNotBlank())
    }

    @Test
    fun `remoteInfo is null for a local-only repo with no remote`() = runTest {
        val repo = repository()
        val dir = temp.newFolder("local-only")
        repo.init(dir)
        writeFile(dir, "a.txt", "x\n")
        repo.stage(dir, "a.txt")
        repo.setCommitMessage(dir, "c")
        repo.commit(dir)

        assertNull(repo.remoteInfo(dir))
    }

    @Test
    fun `remoteInfo is null for a non-repository directory`() = runTest {
        val repo = repository()
        assertNull(repo.remoteInfo(temp.newFolder("plain")))
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
        override fun credentialsForHost(host: String): GitCredentials? = byHost[host]
        override fun hasCredentials(host: String): Boolean = byHost.containsKey(host)
        override fun save(host: String, credentials: GitCredentials) { byHost[host] = credentials }
        override fun clear(host: String) { byHost.remove(host) }
        override val changes = kotlinx.coroutines.flow.emptyFlow<Unit>()
    }
}
