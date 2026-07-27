package com.ahmadkharfan.androidstudiolite.feature.editor.git

import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds
import androidx.lifecycle.viewModelScope
import com.ahmadkharfan.androidstudiolite.domain.model.ActiveOperation
import com.ahmadkharfan.androidstudiolite.domain.model.CloneOptions
import com.ahmadkharfan.androidstudiolite.domain.model.CloneProgress
import com.ahmadkharfan.androidstudiolite.domain.model.GitAuthorConfig
import com.ahmadkharfan.androidstudiolite.domain.model.GitAuthorConfigState
import com.ahmadkharfan.androidstudiolite.domain.model.GitBranch
import com.ahmadkharfan.androidstudiolite.domain.model.GitCommit
import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffHunk
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffKind
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffLine
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffTarget
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileDiff
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileState
import com.ahmadkharfan.androidstudiolite.domain.model.GitIndexStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitRemote
import com.ahmadkharfan.androidstudiolite.domain.model.GitRemoteInfo
import com.ahmadkharfan.androidstudiolite.domain.model.GitRepositoryState
import com.ahmadkharfan.androidstudiolite.domain.model.GitState
import com.ahmadkharfan.androidstudiolite.domain.model.GitSubmodule
import com.ahmadkharfan.androidstudiolite.domain.model.GitSubmoduleStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitSyncResult
import com.ahmadkharfan.androidstudiolite.domain.model.GitUpstream
import com.ahmadkharfan.androidstudiolite.domain.model.GitWorktreeStatus
import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.Project
import com.ahmadkharfan.androidstudiolite.domain.model.PullMode
import com.ahmadkharfan.androidstudiolite.domain.model.GitBlameLine
import com.ahmadkharfan.androidstudiolite.domain.model.GitConflictEntry
import com.ahmadkharfan.androidstudiolite.domain.model.GitFastForwardMode
import com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationResult
import com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitLogPage
import com.ahmadkharfan.androidstudiolite.domain.model.GitResetMode
import com.ahmadkharfan.androidstudiolite.domain.model.GitStash
import com.ahmadkharfan.androidstudiolite.domain.model.GitTag
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubDeviceAuthState
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubDeviceAuthenticator
import com.ahmadkharfan.androidstudiolite.domain.repository.GitOperationMonitor
import com.ahmadkharfan.androidstudiolite.domain.repository.GitRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import com.ahmadkharfan.androidstudiolite.domain.usecase.ProjectPathResolver
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GitPanelControllersTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val root = File("/projects/sample")
    private val repository = FakeGitRepository()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `submodule actions load close initialise and update with visible outcomes`() = runBlocking {
        repository.submoduleItems = listOf(
            GitSubmodule(
                name = "library",
                path = "modules/library",
                url = "https://example.com/library.git",
                status = GitSubmoduleStatus.CHECKED_OUT,
            ),
        )
        val viewModel = readyViewModel()

        viewModel.onOpenSubmodules()
        val opened = withTimeout(5.seconds) {
            viewModel.state.first { it.submodulesVisible && !it.submodulesLoading }
        }
        assertEquals(repository.submoduleItems, opened.submodules)

        viewModel.onCloseSubmodules()
        assertFalse(viewModel.state.value.submodulesVisible)

        viewModel.onInitSubmodules()
        withTimeout(5.seconds) {
            viewModel.state.first { it.statusMessage == "Submodules initialised" }
        }
        assertEquals(1, repository.submoduleInitCalls)

        viewModel.onStatusMessageShown()
        viewModel.onUpdateSubmodules()
        withTimeout(5.seconds) {
            viewModel.state.first { it.statusMessage == "Submodules updated" }
        }
        assertEquals(1, repository.submoduleUpdateCalls)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `bootstrap actions edit confirm and dismiss the dialog`() = runBlocking {
        val viewModel = readyViewModel()

        viewModel.onOpenBootstrap()
        assertTrue(viewModel.state.value.bootstrapVisible)
        viewModel.onBootstrapInitialCommitChanged(false)
        viewModel.onBootstrapMessageChanged("Release baseline")
        assertFalse(viewModel.state.value.bootstrapInitialCommit)
        assertEquals("Release baseline", viewModel.state.value.bootstrapMessage)

        viewModel.onBootstrapInitialCommitChanged(true)
        viewModel.onConfirmBootstrap()
        withTimeout(5.seconds) {
            viewModel.state.first { it.statusMessage == "Version control enabled" }
        }
        assertEquals(1, repository.bootstrapCalls)
        assertEquals("Release baseline", repository.bootstrapMessage)
        assertFalse(viewModel.state.value.bootstrapVisible)

        viewModel.onOpenBootstrap()
        viewModel.onDismissBootstrap()
        assertFalse(viewModel.state.value.bootstrapVisible)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `author actions prefill edit persist clear and dismiss without saving`() = runBlocking {
        repository.authorConfig = GitAuthorConfigState(
            effective = GitAuthorConfig("App Author", "app@example.com"),
            local = GitAuthorConfig("Local Author", "local@example.com"),
            appGlobal = GitAuthorConfig("App Author", "app@example.com"),
        )
        val viewModel = readyViewModel()

        viewModel.onOpenAuthorDialog()
        withTimeout(5.seconds) {
            viewModel.state.first { it.authorDialogVisible }
        }
        assertEquals("Local Author", viewModel.state.value.authorName)
        assertEquals("local@example.com", viewModel.state.value.authorEmail)

        viewModel.onAuthorNameChanged("  New Author  ")
        viewModel.onAuthorEmailChanged("  new@example.com  ")
        assertEquals("  New Author  ", viewModel.state.value.authorName)
        assertEquals("  new@example.com  ", viewModel.state.value.authorEmail)
        viewModel.onSaveLocalAuthor()
        withTimeout(5.seconds) {
            viewModel.state.first { it.statusMessage == "Local Git author saved" }
        }
        assertEquals(GitAuthorConfig("New Author", "new@example.com"), repository.savedLocalAuthor)

        viewModel.onOpenAuthorDialog()
        withTimeout(5.seconds) {
            viewModel.state.first { it.authorDialogVisible }
        }
        viewModel.onUseAppAuthor()
        withTimeout(5.seconds) {
            viewModel.state.first { it.statusMessage == "Using app Git author" }
        }
        assertNull(repository.savedLocalAuthor)

        viewModel.onOpenAuthorDialog()
        withTimeout(5.seconds) {
            viewModel.state.first { it.authorDialogVisible }
        }
        val saveCalls = repository.setLocalAuthorCalls
        viewModel.onAuthorNameChanged("Unsaved")
        viewModel.onDismissAuthorDialog()
        assertFalse(viewModel.state.value.authorDialogVisible)
        assertEquals(saveCalls, repository.setLocalAuthorCalls)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `single stage and unstage move only the requested path`() = runBlocking {
        repository.state.value = GitState(
            files = listOf(
                GitFileState("modified.kt", worktreeStatus = GitWorktreeStatus.MODIFIED),
                GitFileState("staged.kt", indexStatus = GitIndexStatus.MODIFIED),
            ),
        )
        val viewModel = readyViewModel()

        viewModel.onStage("modified.kt")
        withTimeout(5.seconds) {
            viewModel.state.first { state ->
                state.stagedChanges.map { it.path }.toSet() == setOf("modified.kt", "staged.kt") &&
                    state.unstagedChanges.isEmpty()
            }
        }
        assertEquals(listOf("modified.kt"), repository.stageCalls)

        viewModel.onUnstage("staged.kt")
        withTimeout(5.seconds) {
            viewModel.state.first { state ->
                state.stagedChanges.single().path == "modified.kt" &&
                    state.unstagedChanges.single().path == "staged.kt"
            }
        }
        assertEquals(listOf("staged.kt"), repository.unstageCalls)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `stage all and unstage all move every change`() = runBlocking {
        repository.state.value = GitState(files = workingFiles())
        val viewModel = readyViewModel()

        viewModel.onStageAll()
        withTimeout(5.seconds) {
            viewModel.state.first { it.stagedChanges.map { change -> change.path }.toSet() == ALL_PATHS }
        }
        assertEquals(1, repository.stageAllCalls)

        viewModel.onUnstageAll()
        withTimeout(5.seconds) {
            viewModel.state.first { it.stagedChanges.isEmpty() && it.allChangePaths == ALL_PATHS }
        }
        assertEquals(1, repository.unstageAllCalls)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `selection actions toggle paths sections and the complete change set`() = runBlocking {
        repository.state.value = GitState(files = workingFiles())
        val viewModel = readyViewModel()

        viewModel.onToggleSelect("modified.kt")
        assertEquals(setOf("modified.kt"), viewModel.state.value.selectedPaths)
        viewModel.onToggleSelect("modified.kt")
        assertTrue(viewModel.state.value.selectedPaths.isEmpty())

        viewModel.onToggleSectionSelect(listOf("modified.kt", "new.kt"), true)
        assertEquals(setOf("modified.kt", "new.kt"), viewModel.state.value.selectedPaths)
        viewModel.onToggleSectionSelect(listOf("modified.kt", "new.kt"), false)
        assertTrue(viewModel.state.value.selectedPaths.isEmpty())

        viewModel.onSelectAllChanges()
        assertEquals(ALL_PATHS, viewModel.state.value.selectedPaths)
        viewModel.onClearSelection()
        assertTrue(viewModel.state.value.selectedPaths.isEmpty())
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `bulk staging and restore use exactly the eligible selected paths then clear selection`() = runBlocking {
        repository.state.value = GitState(files = workingFiles())
        val viewModel = readyViewModel()

        viewModel.onToggleSectionSelect(listOf("modified.kt", "new.kt", "staged.kt"), true)
        viewModel.onStageSelected()
        withTimeout(5.seconds) {
            viewModel.state.first { it.selectedPaths.isEmpty() && repository.stageCalls.size == 2 }
        }
        assertEquals(setOf("modified.kt", "new.kt"), repository.stageCalls.toSet())

        viewModel.onToggleSectionSelect(listOf("modified.kt", "staged.kt"), true)
        viewModel.onUnstageSelected()
        withTimeout(5.seconds) {
            viewModel.state.first { it.selectedPaths.isEmpty() && repository.unstageCalls.size == 2 }
        }
        assertEquals(setOf("modified.kt", "staged.kt"), repository.unstageCalls.toSet())

        viewModel.onToggleSectionSelect(listOf("modified.kt", "new.kt"), true)
        viewModel.onRevertSelected()
        assertEquals(listOf("modified.kt"), viewModel.state.value.pendingRestorePaths)
        viewModel.onConfirmRestore()
        awaitCondition { repository.restoreCalls.isNotEmpty() }
        assertEquals(listOf(listOf("modified.kt")), repository.restoreCalls)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `selecting a change loads its diff and closing clears it`() = runBlocking {
        repository.state.value = GitState(files = workingFiles())
        val viewModel = readyViewModel()

        viewModel.onSelectChange("modified.kt", GitDiffTarget.INDEX_TO_WORKTREE)
        val selected = withTimeout(5.seconds) {
            viewModel.state.first { it.selectedPath == "modified.kt" && it.diffLines.isNotEmpty() }
        }
        assertEquals(GitDiffTarget.INDEX_TO_WORKTREE, selected.selectedDiffTarget)
        assertEquals(listOf("modified.kt"), repository.worktreeDiffCalls)

        viewModel.onCloseDiff()
        assertNull(viewModel.state.value.selectedPath)
        assertTrue(viewModel.state.value.diffLines.isEmpty())
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `operation dialogs confirm or dismiss abort and restore while continue resumes rebase`() = runBlocking {
        repository.state.value = GitState(files = emptyList(), repositoryState = GitRepositoryState.REBASING)
        val viewModel = readyViewModel()

        viewModel.onContinueOperation()
        awaitCondition { repository.rebaseContinueCalls == 1 }

        viewModel.onRequestAbortOperation()
        assertTrue(viewModel.state.value.abortConfirmVisible)
        viewModel.onConfirmAbortOperation()
        awaitCondition { repository.abortCalls == listOf("rebase") }
        assertFalse(viewModel.state.value.abortConfirmVisible)

        viewModel.onRequestAbortOperation()
        viewModel.onDismissAbortOperation()
        assertFalse(viewModel.state.value.abortConfirmVisible)
        assertEquals(listOf("rebase"), repository.abortCalls)

        viewModel.onRequestRestore("modified.kt")
        assertEquals(listOf("modified.kt"), viewModel.state.value.pendingRestorePaths)
        viewModel.onConfirmRestore()
        awaitCondition { repository.restoreCalls.size == 1 }
        assertTrue(viewModel.state.value.pendingRestorePaths.isEmpty())

        viewModel.onRequestRestore("kept.kt")
        viewModel.onDismissRestore()
        assertTrue(viewModel.state.value.pendingRestorePaths.isEmpty())
        assertEquals(listOf(listOf("modified.kt")), repository.restoreCalls)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `clean preview follows ignored choice confirm cleans and dismiss clears preview`() = runBlocking {
        val viewModel = readyViewModel()

        viewModel.onPreviewClean()
        withTimeout(5.seconds) { viewModel.state.first { it.cleanPreview == listOf("build/") } }
        assertEquals(listOf(true to false), repository.cleanCalls)

        viewModel.onCleanIncludeIgnoredChanged(true)
        withTimeout(5.seconds) {
            viewModel.state.first { it.cleanPreview == listOf("build/", ".cache/") }
        }
        assertTrue(viewModel.state.value.cleanIncludeIgnored)
        assertEquals(listOf(true to false, true to true), repository.cleanCalls)

        viewModel.onConfirmClean()
        withTimeout(5.seconds) {
            viewModel.state.first { it.cleanPreview == null && it.statusMessage == "Removed 2 path(s)" }
        }
        assertEquals(false to true, repository.cleanCalls.last())

        viewModel.onPreviewClean()
        withTimeout(5.seconds) { viewModel.state.first { it.cleanPreview != null } }
        viewModel.onDismissClean()
        assertNull(viewModel.state.value.cleanPreview)
        viewModel.viewModelScope.cancel()
    }

    private suspend fun readyViewModel(): GitPanelViewModel {
        val viewModel = viewModel()
        withTimeout(5.seconds) { viewModel.state.first { !it.loading } }
        return viewModel
    }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(5.seconds) {
            while (!condition()) delay(1.milliseconds)
        }
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
            override fun credentialsForHost(host: String): GitCredentials? = null
            override fun hasCredentials(host: String): Boolean = false
            override fun save(host: String, credentials: GitCredentials) = Unit
            override fun clear(host: String) = Unit
            override val changes = emptyFlow<Unit>()
        },
        authenticator = object : GitHubDeviceAuthenticator {
            override val isConfigured: Boolean = false
            override fun authenticate(): Flow<GitHubDeviceAuthState> = emptyFlow()
        },
        ioDispatcher = Dispatchers.Unconfined,
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
        val stageCalls = mutableListOf<String>()
        val unstageCalls = mutableListOf<String>()
        var stageAllCalls = 0
        var unstageAllCalls = 0
        val worktreeDiffCalls = mutableListOf<String>()
        var rebaseContinueCalls = 0
        val abortCalls = mutableListOf<String>()
        val restoreCalls = mutableListOf<List<String>>()
        val cleanCalls = mutableListOf<Pair<Boolean, Boolean>>()
        var submoduleItems = emptyList<GitSubmodule>()
        var submoduleInitCalls = 0
        var submoduleUpdateCalls = 0
        var bootstrapCalls = 0
        var bootstrapMessage: String? = null
        var authorConfig = GitAuthorConfigState(
            effective = GitAuthorConfig("App Author", "app@example.com"),
            local = null,
            appGlobal = null,
        )
        var savedLocalAuthor: GitAuthorConfig? = null
        var setLocalAuthorCalls = 0

        override fun observeState(repoDir: File): StateFlow<GitState> = state
        override suspend fun refresh(repoDir: File, includeIgnored: Boolean) = Unit
        override fun clone(
            url: String,
            destination: File,
            options: CloneOptions,
            credentials: GitCredentials?,
        ): Flow<CloneProgress> = emptyFlow()
        override suspend fun diffIndexToWorktree(repoDir: File, path: String, force: Boolean): GitFileDiff {
            worktreeDiffCalls += path
            return GitFileDiff(
                path = path,
                hunks = listOf(
                    GitDiffHunk(
                        oldStart = 1,
                        oldCount = 1,
                        newStart = 1,
                        newCount = 1,
                        lines = listOf(GitDiffLine(GitDiffKind.MODIFIED, "changed", 1, 1)),
                    ),
                ),
            )
        }
        override suspend fun diffHeadToIndex(repoDir: File, path: String, force: Boolean) =
            GitFileDiff(path)
        override suspend fun diffCommitToParent(repoDir: File, commitId: String, path: String, force: Boolean) =
            GitFileDiff(path)
        override suspend fun diffIndexToBuffer(repoDir: File, path: String, buffer: String) =
            GitFileDiff(path)
        override suspend fun stageHunk(
            repoDir: File,
            path: String,
            hunk: GitDiffHunk,
        ) = Unit
        override suspend fun unstageHunk(
            repoDir: File,
            path: String,
            hunk: GitDiffHunk,
        ) = Unit
        override suspend fun stage(repoDir: File, path: String) {
            stageCalls += path
            state.value = state.value.copy(files = state.value.files.map { file ->
                if (file.path == path) file.staged() else file
            })
        }
        override suspend fun unstage(repoDir: File, path: String) {
            unstageCalls += path
            state.value = state.value.copy(files = state.value.files.map { file ->
                if (file.path == path) file.unstaged() else file
            })
        }
        override suspend fun stageAll(repoDir: File) {
            stageAllCalls++
            state.value = state.value.copy(files = state.value.files.map { it.staged() })
        }
        override suspend fun unstageAll(repoDir: File) {
            unstageAllCalls++
            state.value = state.value.copy(files = state.value.files.map { it.unstaged() })
        }
        override suspend fun setCommitMessage(repoDir: File, message: String) = Unit
        override suspend fun commit(repoDir: File, amend: Boolean): String = "unused"
        override suspend fun getAuthorConfig(repoDir: File): GitAuthorConfigState = authorConfig
        override suspend fun setLocalAuthor(repoDir: File, config: GitAuthorConfig?) {
            setLocalAuthorCalls++
            savedLocalAuthor = config
            authorConfig = authorConfig.copy(local = config)
        }
        override suspend fun setAppGlobalAuthor(config: GitAuthorConfig) = Unit
        override suspend fun listRemotes(repoDir: File): List<GitRemote> = emptyList()
        override suspend fun addRemote(repoDir: File, name: String, url: String) = Unit
        override suspend fun setRemoteUrl(repoDir: File, name: String, url: String) = Unit
        override suspend fun removeRemote(repoDir: File, name: String) = Unit
        override suspend fun fetch(repoDir: File, remote: String?, prune: Boolean) = GitSyncResult(true, "")
        override suspend fun setUpstream(
            repoDir: File,
            branch: String,
            remote: String,
            remoteBranch: String,
        ) = Unit
        override suspend fun upstreamOf(repoDir: File, branch: String): GitUpstream? = null
        override suspend fun branches(repoDir: File): List<GitBranch> = emptyList()
        override suspend fun createBranch(repoDir: File, name: String, checkout: Boolean) = Unit
        override suspend fun checkout(repoDir: File, name: String) = Unit
        override suspend fun checkoutRemoteBranch(repoDir: File, remoteBranch: String, localName: String?) = Unit
        override suspend fun renameBranch(repoDir: File, oldName: String, newName: String) = Unit
        override suspend fun deleteBranch(repoDir: File, name: String, force: Boolean) = Unit
        override suspend fun publishBranch(repoDir: File, name: String) = GitSyncResult(true, "")
        override suspend fun log(repoDir: File, max: Int): List<GitCommit> = emptyList()
        override suspend fun log(repoDir: File, cursor: String?, limit: Int) =
            GitLogPage(emptyList(), null)
        override suspend fun fileHistory(repoDir: File, path: String, cursor: String?, limit: Int) =
            GitLogPage(emptyList(), null)
        @Suppress("UNUSED_PARAMETER")
        override suspend fun commitDetails(repoDir: File, commitId: String) = error("unused")
        override suspend fun blame(repoDir: File, path: String) =
            emptyList<GitBlameLine>()
        override suspend fun isShallow(repoDir: File) = false
        override suspend fun deepen(repoDir: File) = GitSyncResult(true, "")
        override suspend fun listTags(repoDir: File) =
            emptyList<GitTag>()
        override suspend fun createTag(
            repoDir: File,
            name: String,
            message: String?,
            targetCommit: String?,
        ) = Unit
        override suspend fun deleteTag(repoDir: File, name: String) = Unit
        override suspend fun pushTag(repoDir: File, name: String) = GitSyncResult(true, "")
        override suspend fun pushAllTags(repoDir: File) = GitSyncResult(true, "")
        override suspend fun stashCreate(repoDir: File, message: String?, includeUntracked: Boolean): String? = null
        override suspend fun stashList(repoDir: File) =
            emptyList<GitStash>()
        override suspend fun stashApply(repoDir: File, index: Int) = Unit
        override suspend fun stashPop(repoDir: File, index: Int) = Unit
        override suspend fun stashDrop(repoDir: File, index: Int) = Unit
        override suspend fun merge(
            repoDir: File,
            ref: String,
            ffMode: GitFastForwardMode,
            message: String?,
        ) = GitIntegrationResult(
            GitIntegrationStatus.MERGED,
        )
        override suspend fun mergeAbort(repoDir: File) {
            abortCalls += "merge"
        }
        override suspend fun cherryPick(repoDir: File, commitId: String) =
            GitIntegrationResult(
                GitIntegrationStatus.APPLIED,
            )
        override suspend fun cherryPickAbort(repoDir: File) {
            abortCalls += "cherry-pick"
        }
        override suspend fun revert(repoDir: File, commitId: String) =
            GitIntegrationResult(
                GitIntegrationStatus.APPLIED,
            )
        override suspend fun revertAbort(repoDir: File) {
            abortCalls += "revert"
        }
        override suspend fun rebase(repoDir: File, upstreamRef: String) =
            GitIntegrationResult(
                GitIntegrationStatus.MERGED,
            )
        override suspend fun rebaseContinue(repoDir: File) =
            GitIntegrationResult(
                GitIntegrationStatus.MERGED,
            ).also { rebaseContinueCalls++ }
        override suspend fun rebaseSkip(repoDir: File) =
            GitIntegrationResult(
                GitIntegrationStatus.MERGED,
            )
        override suspend fun rebaseAbort(repoDir: File) =
            GitIntegrationResult(
                GitIntegrationStatus.ABORTED,
            ).also { abortCalls += "rebase" }
        override suspend fun conflictEntries(repoDir: File) =
            emptyList<GitConflictEntry>()
        override suspend fun resolveAcceptOurs(repoDir: File, path: String) = Unit
        override suspend fun resolveAcceptTheirs(repoDir: File, path: String) = Unit
        override suspend fun markResolved(repoDir: File, path: String) = Unit
        override suspend fun restoreFiles(repoDir: File, paths: List<String>) {
            restoreCalls += paths
        }
        override suspend fun reset(
            repoDir: File,
            commitId: String,
            mode: GitResetMode,
        ) = Unit
        override suspend fun clean(repoDir: File, dryRun: Boolean, includeIgnored: Boolean): List<String> {
            cleanCalls += dryRun to includeIgnored
            return if (includeIgnored) listOf("build/", ".cache/") else listOf("build/")
        }
        override suspend fun submodules(repoDir: File): List<GitSubmodule> = submoduleItems
        override suspend fun submoduleInit(repoDir: File) {
            submoduleInitCalls++
        }
        override suspend fun submoduleUpdate(repoDir: File) {
            submoduleUpdateCalls++
        }
        @Suppress("UNUSED_PARAMETER")
        override suspend fun bootstrapRepository(repoDir: File, initialCommitMessage: String?): String? {
            bootstrapCalls++
            bootstrapMessage = initialCommitMessage
            return initialCommitMessage?.let { "abc123" }
        }
        override suspend fun addToGitignore(repoDir: File, path: String) = Unit
        override suspend fun push(repoDir: File, setUpstreamIfMissing: Boolean) = GitSyncResult(true, "")
        override suspend fun pushForceWithLease(repoDir: File) = GitSyncResult(true, "")
        override suspend fun pull(repoDir: File, mode: PullMode) = GitSyncResult(true, "")
        override suspend fun isRepository(repoDir: File): Boolean = true
        override suspend fun remoteInfo(repoDir: File): GitRemoteInfo? = null
        override suspend fun init(repoDir: File) = Unit

        private fun GitFileState.staged() = copy(
            indexStatus = if (worktreeStatus == GitWorktreeStatus.UNTRACKED) {
                GitIndexStatus.ADDED
            } else {
                GitIndexStatus.MODIFIED
            },
            worktreeStatus = GitWorktreeStatus.UNCHANGED,
        )

        private fun GitFileState.unstaged() = copy(
            indexStatus = GitIndexStatus.UNCHANGED,
            worktreeStatus = if (indexStatus == GitIndexStatus.ADDED) {
                GitWorktreeStatus.UNTRACKED
            } else {
                GitWorktreeStatus.MODIFIED
            },
        )
    }

    private companion object {
        val ALL_PATHS = setOf("staged.kt", "modified.kt", "new.kt")

        fun workingFiles() = listOf(
            GitFileState("staged.kt", indexStatus = GitIndexStatus.MODIFIED),
            GitFileState("modified.kt", worktreeStatus = GitWorktreeStatus.MODIFIED),
            GitFileState("new.kt", worktreeStatus = GitWorktreeStatus.UNTRACKED),
        )
    }
}
