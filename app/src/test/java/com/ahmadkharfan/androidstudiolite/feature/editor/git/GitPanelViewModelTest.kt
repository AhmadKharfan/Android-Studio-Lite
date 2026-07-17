package com.ahmadkharfan.androidstudiolite.feature.editor.git

import androidx.lifecycle.viewModelScope
import com.ahmadkharfan.androidstudiolite.domain.model.ActiveOperation
import com.ahmadkharfan.androidstudiolite.domain.model.CloneOptions
import com.ahmadkharfan.androidstudiolite.domain.model.CloneProgress
import com.ahmadkharfan.androidstudiolite.domain.model.GitBranch
import com.ahmadkharfan.androidstudiolite.domain.model.GitAuthorConfig
import com.ahmadkharfan.androidstudiolite.domain.model.GitAuthorConfigState
import com.ahmadkharfan.androidstudiolite.domain.model.GitCommit
import com.ahmadkharfan.androidstudiolite.domain.model.GitConflictInfo
import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.model.GitException
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffLine
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileState
import com.ahmadkharfan.androidstudiolite.domain.model.GitHeadState
import com.ahmadkharfan.androidstudiolite.domain.model.GitIndexStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitRemoteInfo
import com.ahmadkharfan.androidstudiolite.domain.model.GitRemote
import com.ahmadkharfan.androidstudiolite.domain.model.GitState
import com.ahmadkharfan.androidstudiolite.domain.model.GitSyncResult
import com.ahmadkharfan.androidstudiolite.domain.model.GitUpstream
import com.ahmadkharfan.androidstudiolite.domain.model.PullMode
import com.ahmadkharfan.androidstudiolite.domain.model.GitWorktreeStatus
import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.Project
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import com.ahmadkharfan.androidstudiolite.domain.repository.GitOperationMonitor
import com.ahmadkharfan.androidstudiolite.domain.repository.GitRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import com.ahmadkharfan.androidstudiolite.domain.usecase.ProjectPathResolver
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GitPanelViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val root = File("/projects/sample")
    private val repository = FakeGitRepository()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `layered files are bucketed and a dual dimension file appears twice`() = runBlocking {
        val viewModel = viewModel()
        repository.state.value = GitState(
            files = listOf(
                GitFileState("conflict.kt", conflictStage = GitConflictInfo(stages = emptySet(), description = "both modified")),
                GitFileState("staged.kt", indexStatus = GitIndexStatus.ADDED),
                GitFileState("changed.kt", worktreeStatus = GitWorktreeStatus.MODIFIED),
                GitFileState("new.kt", worktreeStatus = GitWorktreeStatus.UNTRACKED),
                GitFileState(
                    "both.kt",
                    indexStatus = GitIndexStatus.MODIFIED,
                    worktreeStatus = GitWorktreeStatus.MODIFIED,
                ),
            ),
            headState = GitHeadState.Branch("main"),
        )

        val state = withTimeout(5_000) { viewModel.state.first { it.untrackedChanges.isNotEmpty() } }
        assertEquals(listOf("conflict.kt"), state.conflicts.map { it.path })
        assertEquals(listOf("staged.kt", "both.kt"), state.stagedChanges.map { it.path })
        assertEquals(listOf("changed.kt", "both.kt"), state.unstagedChanges.map { it.path })
        assertEquals(listOf("new.kt"), state.untrackedChanges.map { it.path })
        assertEquals("both modified", state.conflicts.single().description)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `staged rename displays old to new while retaining new path for actions`() = runBlocking {
        val viewModel = viewModel()
        repository.state.value = GitState(
            files = listOf(
                GitFileState("new.kt", oldPath = "old.kt", indexStatus = GitIndexStatus.RENAMED),
            ),
        )

        val rename = withTimeout(5_000) { viewModel.state.first { it.stagedChanges.isNotEmpty() } }
            .stagedChanges.single()
        assertEquals("old.kt → new.kt", rename.displayPath)
        assertEquals("new.kt", rename.path)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `commit and push reports success after both operations`() = runBlocking {
        repository.state.value = GitState(
            files = listOf(GitFileState("staged.kt", indexStatus = GitIndexStatus.ADDED)),
            commitMessage = "message",
        )
        val viewModel = viewModel()
        withTimeout(5_000) { viewModel.state.first { it.stagedChanges.isNotEmpty() } }

        viewModel.onCommitAndPush()
        val result = withTimeout(5_000) { viewModel.state.first { it.statusMessage?.contains("Committed and pushed") == true } }

        assertEquals(1, repository.commitCalls)
        assertEquals(1, repository.pushCalls)
        assertTrue(result.statusMessage.orEmpty().contains("abc1234"))
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `push failure after commit is reported as partial success`() = runBlocking {
        repository.failPush = true
        repository.state.value = GitState(
            files = listOf(GitFileState("staged.kt", indexStatus = GitIndexStatus.ADDED)),
            commitMessage = "message",
        )
        val viewModel = viewModel()
        withTimeout(5_000) { viewModel.state.first { it.stagedChanges.isNotEmpty() } }

        viewModel.onCommitAndPush()
        val result = withTimeout(5_000) { viewModel.state.first { it.statusMessage?.contains("but push failed") == true } }

        assertEquals(1, repository.commitCalls)
        assertEquals(1, repository.pushCalls)
        assertTrue(result.statusMessage.orEmpty().contains("Authentication failed"))
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `amend pre-fills previous message and allows message-only commit`() = runBlocking {
        repository.lastCommitMessage = "previous message"
        val viewModel = viewModel()
        withTimeout(5_000) { viewModel.state.first { !it.loading } }

        viewModel.onAmendChanged(true)
        val amended = withTimeout(5_000) {
            viewModel.state.first { it.amend && it.commitMessage == "previous message" }
        }

        assertEquals("previous message", amended.commitMessage)
        assertTrue(amended.canCommit)
        viewModel.onAmendChanged(false)
        assertEquals("", viewModel.state.value.commitMessage)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `remote editor validates name and URL then adds a unique remote`() = runBlocking {
        val viewModel = viewModel()
        withTimeout(5_000) { viewModel.state.first { !it.loading } }
        viewModel.onOpenRemotes()
        viewModel.onAddRemote()
        viewModel.onRemoteNameChanged("bad name")
        viewModel.onRemoteUrlChanged("ssh://example.com/repo.git")
        viewModel.onSaveRemote()

        assertTrue(viewModel.state.value.remoteNameError != null)
        assertTrue(viewModel.state.value.remoteUrlError != null)

        viewModel.onRemoteNameChanged("origin")
        viewModel.onRemoteUrlChanged("https://example.com/repo.git")
        viewModel.onSaveRemote()
        val saved = withTimeout(5_000) { viewModel.state.first { it.remotes.any { remote -> remote.name == "origin" } } }
        assertEquals("https://example.com/repo.git", saved.remotes.single().url)

        viewModel.onAddRemote()
        viewModel.onRemoteNameChanged("origin")
        viewModel.onRemoteUrlChanged("https://example.org/other.git")
        viewModel.onSaveRemote()
        assertEquals("A remote with this name already exists", viewModel.state.value.remoteNameError)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `selected pull mode is passed to repository`() = runBlocking {
        val viewModel = viewModel()
        withTimeout(5_000) { viewModel.state.first { !it.loading } }

        viewModel.onPullModeChanged(PullMode.REBASE)
        viewModel.onPull()
        withTimeout(5_000) { viewModel.state.first { it.statusMessage?.startsWith("Pull:") == true } }

        assertEquals(PullMode.REBASE, repository.lastPullMode)
        viewModel.viewModelScope.cancel()
    }

    private fun viewModel(): GitPanelViewModel = GitPanelViewModel(
        projectId = "sample",
        projectPathResolver = ProjectPathResolver(FakeProjectRepository(root)),
        gitRepository = repository,
        operationMonitor = object : GitOperationMonitor {
            override fun activeOperation(repoDir: File): StateFlow<ActiveOperation?> = MutableStateFlow(null)
            override fun cancelActiveOperation(repoDir: File): Boolean = false
        },
        credentialStore = object : GitCredentialStore {
            override fun credentialsForUrl(url: String): GitCredentials? = null
            override fun save(host: String, credentials: GitCredentials) = Unit
            override fun clear(host: String) = Unit
        },
    )

    private class FakeProjectRepository(root: File) : ProjectRepository {
        private val project = Project("sample", "Sample", root.absolutePath, "Kotlin", null)
        override fun observeRecentProjects(): Flow<List<Project>> = MutableStateFlow(listOf(project))
        override suspend fun createProject(spec: NewProjectSpec): Project = error("unused")
        override suspend fun registerExistingProject(path: File): Project = error("unused")
        override suspend fun openProject(id: String): Project = project
        override suspend fun deleteProject(id: String) = Unit
        override suspend fun renameProject(id: String, newName: String) = Unit
    }

    private class FakeGitRepository : GitRepository {
        val state = MutableStateFlow(GitState(files = emptyList()))
        var commitCalls = 0
        var pushCalls = 0
        var failPush = false
        var lastCommitMessage: String? = null
        var lastPullMode: PullMode? = null
        private val remotes = mutableListOf<GitRemote>()
        override fun observeState(repoDir: File): StateFlow<GitState> = state
        override suspend fun refresh(repoDir: File, includeIgnored: Boolean) = Unit
        override fun clone(url: String, destination: File, options: CloneOptions, credentials: GitCredentials?): Flow<CloneProgress> = emptyFlow()
        override suspend fun diffIndexToWorktree(repoDir: File, path: String, force: Boolean) =
            com.ahmadkharfan.androidstudiolite.domain.model.GitFileDiff(path)
        override suspend fun diffHeadToIndex(repoDir: File, path: String, force: Boolean) =
            com.ahmadkharfan.androidstudiolite.domain.model.GitFileDiff(path)
        override suspend fun diffCommitToParent(repoDir: File, commitId: String, path: String, force: Boolean) =
            com.ahmadkharfan.androidstudiolite.domain.model.GitFileDiff(path)
        override suspend fun diffIndexToBuffer(repoDir: File, path: String, buffer: String) =
            com.ahmadkharfan.androidstudiolite.domain.model.GitFileDiff(path)
        override suspend fun stageHunk(
            repoDir: File,
            path: String,
            hunk: com.ahmadkharfan.androidstudiolite.domain.model.GitDiffHunk,
        ) = Unit
        override suspend fun unstageHunk(
            repoDir: File,
            path: String,
            hunk: com.ahmadkharfan.androidstudiolite.domain.model.GitDiffHunk,
        ) = Unit
        override suspend fun stage(repoDir: File, path: String) = Unit
        override suspend fun unstage(repoDir: File, path: String) = Unit
        override suspend fun stageAll(repoDir: File) = Unit
        override suspend fun unstageAll(repoDir: File) = Unit
        override suspend fun setCommitMessage(repoDir: File, message: String) = Unit
        override suspend fun commit(repoDir: File, amend: Boolean): String {
            commitCalls++
            return "abc123456789"
        }
        override suspend fun getAuthorConfig(repoDir: File) = GitAuthorConfigState(
            effective = GitAuthorConfig("Test", "test@example.com"),
            local = null,
            appGlobal = null,
        )
        override suspend fun setLocalAuthor(repoDir: File, config: GitAuthorConfig?) = Unit
        override suspend fun setAppGlobalAuthor(config: GitAuthorConfig) = Unit
        override suspend fun listRemotes(repoDir: File): List<GitRemote> = remotes.toList()
        override suspend fun addRemote(repoDir: File, name: String, url: String) { remotes += GitRemote(name, url) }
        override suspend fun setRemoteUrl(repoDir: File, name: String, url: String) {
            val index = remotes.indexOfFirst { it.name == name }
            remotes[index] = remotes[index].copy(url = url)
        }
        override suspend fun removeRemote(repoDir: File, name: String) { remotes.removeAll { it.name == name } }
        override suspend fun fetch(repoDir: File, remote: String?, prune: Boolean) = GitSyncResult(true, "")
        override suspend fun setUpstream(repoDir: File, branch: String, remote: String, remoteBranch: String) = Unit
        override suspend fun upstreamOf(repoDir: File, branch: String): GitUpstream? = null
        override suspend fun branches(repoDir: File): List<GitBranch> = emptyList()
        override suspend fun createBranch(repoDir: File, name: String, checkout: Boolean) = Unit
        override suspend fun checkout(repoDir: File, name: String) = Unit
        override suspend fun checkoutRemoteBranch(repoDir: File, remoteBranch: String, localName: String?) = Unit
        override suspend fun renameBranch(repoDir: File, oldName: String, newName: String) = Unit
        override suspend fun deleteBranch(repoDir: File, name: String, force: Boolean) = Unit
        override suspend fun publishBranch(repoDir: File, name: String) = GitSyncResult(true, "")
        override suspend fun log(repoDir: File, max: Int): List<GitCommit> = lastCommitMessage?.let {
            listOf(GitCommit("id", "id", it, "Test", "test@example.com", 0L))
        }.orEmpty()
        override suspend fun log(repoDir: File, cursor: String?, limit: Int) =
            com.ahmadkharfan.androidstudiolite.domain.model.GitLogPage(emptyList(), null)
        override suspend fun fileHistory(repoDir: File, path: String, cursor: String?, limit: Int) =
            com.ahmadkharfan.androidstudiolite.domain.model.GitLogPage(emptyList(), null)
        override suspend fun commitDetails(repoDir: File, commitId: String) = error("unused")
        override suspend fun blame(repoDir: File, path: String) =
            emptyList<com.ahmadkharfan.androidstudiolite.domain.model.GitBlameLine>()
        override suspend fun isShallow(repoDir: File) = false
        override suspend fun deepen(repoDir: File) = GitSyncResult(true, "")
        override suspend fun listTags(repoDir: File) =
            emptyList<com.ahmadkharfan.androidstudiolite.domain.model.GitTag>()
        override suspend fun createTag(repoDir: File, name: String, message: String?, targetCommit: String?) = Unit
        override suspend fun deleteTag(repoDir: File, name: String) = Unit
        override suspend fun pushTag(repoDir: File, name: String) = GitSyncResult(true, "")
        override suspend fun pushAllTags(repoDir: File) = GitSyncResult(true, "")
        override suspend fun stashCreate(repoDir: File, message: String?, includeUntracked: Boolean): String? = null
        override suspend fun stashList(repoDir: File) =
            emptyList<com.ahmadkharfan.androidstudiolite.domain.model.GitStash>()
        override suspend fun stashApply(repoDir: File, index: Int) = Unit
        override suspend fun stashPop(repoDir: File, index: Int) = Unit
        override suspend fun stashDrop(repoDir: File, index: Int) = Unit
        override suspend fun merge(repoDir: File, ref: String, ffMode: com.ahmadkharfan.androidstudiolite.domain.model.GitFastForwardMode, message: String?) = com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationResult(com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationStatus.MERGED)
        override suspend fun mergeAbort(repoDir: File) = Unit
        override suspend fun cherryPick(repoDir: File, commitId: String) = com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationResult(com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationStatus.APPLIED)
        override suspend fun cherryPickAbort(repoDir: File) = Unit
        override suspend fun revert(repoDir: File, commitId: String) = com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationResult(com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationStatus.APPLIED)
        override suspend fun revertAbort(repoDir: File) = Unit
        override suspend fun rebase(repoDir: File, upstreamRef: String) = com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationResult(com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationStatus.MERGED)
        override suspend fun rebaseContinue(repoDir: File) = com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationResult(com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationStatus.MERGED)
        override suspend fun rebaseSkip(repoDir: File) = com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationResult(com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationStatus.MERGED)
        override suspend fun rebaseAbort(repoDir: File) = com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationResult(com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationStatus.ABORTED)
        override suspend fun conflictEntries(repoDir: File) = emptyList<com.ahmadkharfan.androidstudiolite.domain.model.GitConflictEntry>()
        override suspend fun resolveAcceptOurs(repoDir: File, path: String) = Unit
        override suspend fun resolveAcceptTheirs(repoDir: File, path: String) = Unit
        override suspend fun markResolved(repoDir: File, path: String) = Unit
        override suspend fun restoreFiles(repoDir: File, paths: List<String>) = Unit
        override suspend fun reset(repoDir: File, commitId: String, mode: com.ahmadkharfan.androidstudiolite.domain.model.GitResetMode) = Unit
        override suspend fun clean(repoDir: File, dryRun: Boolean, includeIgnored: Boolean) = emptyList<String>()
        override suspend fun submodules(repoDir: File) =
            emptyList<com.ahmadkharfan.androidstudiolite.domain.model.GitSubmodule>()
        override suspend fun submoduleInit(repoDir: File) = Unit
        override suspend fun submoduleUpdate(repoDir: File) = Unit
        override suspend fun bootstrapRepository(repoDir: File, initialCommitMessage: String?): String? = null
        override suspend fun addToGitignore(repoDir: File, path: String) = Unit
        override suspend fun push(repoDir: File, setUpstreamIfMissing: Boolean): GitSyncResult {
            pushCalls++
            if (failPush) throw GitException.Auth("denied")
            return GitSyncResult(true, "")
        }
        override suspend fun pushForceWithLease(repoDir: File): GitSyncResult = GitSyncResult(true, "")
        override suspend fun pull(repoDir: File, mode: PullMode): GitSyncResult {
            lastPullMode = mode
            return GitSyncResult(true, "")
        }
        override suspend fun isRepository(repoDir: File): Boolean = true
        override suspend fun remoteInfo(repoDir: File): GitRemoteInfo? = null
        override suspend fun init(repoDir: File) = Unit
    }
}
