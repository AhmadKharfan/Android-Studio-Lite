package com.ahmadkharfan.androidstudiolite.feature.editor.git

import androidx.lifecycle.viewModelScope
import com.ahmadkharfan.androidstudiolite.domain.model.ActiveOperation
import com.ahmadkharfan.androidstudiolite.domain.model.CloneOptions
import com.ahmadkharfan.androidstudiolite.domain.model.CloneProgress
import com.ahmadkharfan.androidstudiolite.domain.model.GitAuthorConfig
import com.ahmadkharfan.androidstudiolite.domain.model.GitAuthorConfigState
import com.ahmadkharfan.androidstudiolite.domain.model.GitBranch
import com.ahmadkharfan.androidstudiolite.domain.model.GitCommit
import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.model.GitRemote
import com.ahmadkharfan.androidstudiolite.domain.model.GitRemoteInfo
import com.ahmadkharfan.androidstudiolite.domain.model.GitState
import com.ahmadkharfan.androidstudiolite.domain.model.GitSubmodule
import com.ahmadkharfan.androidstudiolite.domain.model.GitSubmoduleStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitSyncResult
import com.ahmadkharfan.androidstudiolite.domain.model.GitUpstream
import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.Project
import com.ahmadkharfan.androidstudiolite.domain.model.PullMode
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
        val opened = withTimeout(5_000) {
            viewModel.state.first { it.submodulesVisible && !it.submodulesLoading }
        }
        assertEquals(repository.submoduleItems, opened.submodules)

        viewModel.onCloseSubmodules()
        assertFalse(viewModel.state.value.submodulesVisible)

        viewModel.onInitSubmodules()
        withTimeout(5_000) {
            viewModel.state.first { it.statusMessage == "Submodules initialised" }
        }
        assertEquals(1, repository.submoduleInitCalls)

        viewModel.onStatusMessageShown()
        viewModel.onUpdateSubmodules()
        withTimeout(5_000) {
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
        withTimeout(5_000) {
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
        withTimeout(5_000) {
            viewModel.state.first { it.authorDialogVisible }
        }
        assertEquals("Local Author", viewModel.state.value.authorName)
        assertEquals("local@example.com", viewModel.state.value.authorEmail)

        viewModel.onAuthorNameChanged("  New Author  ")
        viewModel.onAuthorEmailChanged("  new@example.com  ")
        assertEquals("  New Author  ", viewModel.state.value.authorName)
        assertEquals("  new@example.com  ", viewModel.state.value.authorEmail)
        viewModel.onSaveLocalAuthor()
        withTimeout(5_000) {
            viewModel.state.first { it.statusMessage == "Local Git author saved" }
        }
        assertEquals(GitAuthorConfig("New Author", "new@example.com"), repository.savedLocalAuthor)

        viewModel.onOpenAuthorDialog()
        withTimeout(5_000) {
            viewModel.state.first { it.authorDialogVisible }
        }
        viewModel.onUseAppAuthor()
        withTimeout(5_000) {
            viewModel.state.first { it.statusMessage == "Using app Git author" }
        }
        assertNull(repository.savedLocalAuthor)

        viewModel.onOpenAuthorDialog()
        withTimeout(5_000) {
            viewModel.state.first { it.authorDialogVisible }
        }
        val saveCalls = repository.setLocalAuthorCalls
        viewModel.onAuthorNameChanged("Unsaved")
        viewModel.onDismissAuthorDialog()
        assertFalse(viewModel.state.value.authorDialogVisible)
        assertEquals(saveCalls, repository.setLocalAuthorCalls)
        viewModel.viewModelScope.cancel()
    }

    private suspend fun readyViewModel(): GitPanelViewModel {
        val viewModel = viewModel()
        withTimeout(5_000) { viewModel.state.first { !it.loading } }
        return viewModel
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
            emptyList<com.ahmadkharfan.androidstudiolite.domain.model.GitStash>()
        override suspend fun stashApply(repoDir: File, index: Int) = Unit
        override suspend fun stashPop(repoDir: File, index: Int) = Unit
        override suspend fun stashDrop(repoDir: File, index: Int) = Unit
        override suspend fun merge(
            repoDir: File,
            ref: String,
            ffMode: com.ahmadkharfan.androidstudiolite.domain.model.GitFastForwardMode,
            message: String?,
        ) = com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationResult(
            com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationStatus.MERGED,
        )
        override suspend fun mergeAbort(repoDir: File) = Unit
        override suspend fun cherryPick(repoDir: File, commitId: String) =
            com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationResult(
                com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationStatus.APPLIED,
            )
        override suspend fun cherryPickAbort(repoDir: File) = Unit
        override suspend fun revert(repoDir: File, commitId: String) =
            com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationResult(
                com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationStatus.APPLIED,
            )
        override suspend fun revertAbort(repoDir: File) = Unit
        override suspend fun rebase(repoDir: File, upstreamRef: String) =
            com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationResult(
                com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationStatus.MERGED,
            )
        override suspend fun rebaseContinue(repoDir: File) =
            com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationResult(
                com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationStatus.MERGED,
            )
        override suspend fun rebaseSkip(repoDir: File) =
            com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationResult(
                com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationStatus.MERGED,
            )
        override suspend fun rebaseAbort(repoDir: File) =
            com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationResult(
                com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationStatus.ABORTED,
            )
        override suspend fun conflictEntries(repoDir: File) =
            emptyList<com.ahmadkharfan.androidstudiolite.domain.model.GitConflictEntry>()
        override suspend fun resolveAcceptOurs(repoDir: File, path: String) = Unit
        override suspend fun resolveAcceptTheirs(repoDir: File, path: String) = Unit
        override suspend fun markResolved(repoDir: File, path: String) = Unit
        override suspend fun restoreFiles(repoDir: File, paths: List<String>) = Unit
        override suspend fun reset(
            repoDir: File,
            commitId: String,
            mode: com.ahmadkharfan.androidstudiolite.domain.model.GitResetMode,
        ) = Unit
        override suspend fun clean(repoDir: File, dryRun: Boolean, includeIgnored: Boolean) = emptyList<String>()
        override suspend fun submodules(repoDir: File): List<GitSubmodule> = submoduleItems
        override suspend fun submoduleInit(repoDir: File) {
            submoduleInitCalls++
        }
        override suspend fun submoduleUpdate(repoDir: File) {
            submoduleUpdateCalls++
        }
        override suspend fun bootstrapRepository(repoDir: File, initialCommitMessage: String?): String? {
            bootstrapCalls++
            bootstrapMessage = initialCommitMessage
            return "abc123"
        }
        override suspend fun addToGitignore(repoDir: File, path: String) = Unit
        override suspend fun push(repoDir: File, setUpstreamIfMissing: Boolean) = GitSyncResult(true, "")
        override suspend fun pushForceWithLease(repoDir: File) = GitSyncResult(true, "")
        override suspend fun pull(repoDir: File, mode: PullMode) = GitSyncResult(true, "")
        override suspend fun isRepository(repoDir: File): Boolean = true
        override suspend fun remoteInfo(repoDir: File): GitRemoteInfo? = null
        override suspend fun init(repoDir: File) = Unit
    }
}
