package com.ahmadkharfan.androidstudiolite.feature.editor.git

import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslDiffKind
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffKind
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffTarget
import com.ahmadkharfan.androidstudiolite.domain.model.GitAuthorConfig
import com.ahmadkharfan.androidstudiolite.domain.model.PullMode
import com.ahmadkharfan.androidstudiolite.domain.model.GitRepositoryState
import com.ahmadkharfan.androidstudiolite.domain.model.GitException
import com.ahmadkharfan.androidstudiolite.domain.repository.GitRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.GitOperationMonitor
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubDeviceAuthenticator
import com.ahmadkharfan.androidstudiolite.domain.usecase.ProjectPathResolver
import com.ahmadkharfan.androidstudiolite.feature.git.gitErrorMessage
import androidx.lifecycle.viewModelScope
import java.io.File
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

class GitPanelViewModel(
    private val projectId: String,
    private val projectPathResolver: ProjectPathResolver,
    private val gitRepository: GitRepository,
    private val operationMonitor: GitOperationMonitor,
    private val credentialStore: GitCredentialStore,
    private val authenticator: GitHubDeviceAuthenticator,
) : BaseViewModel<GitPanelUiState, Nothing>(
    initialState = GitPanelUiState(authPrompt = GitAuthPromptState(gitHubAvailable = authenticator.isConfigured)),
), GitPanelInteractionListener, GitAuthPromptActions {

    @Volatile
    private var repoDir: File? = null
    private var syncJob: Job? = null

    private val authController = GitAuthController(
        scope = viewModelScope,
        credentialStore = credentialStore,
        authenticator = authenticator,
        emit = { prompt -> updateState { copy(authPrompt = prompt) } },
    )

    init {
        tryToExecute(
            block = {
                projectPathResolver(projectId).also { gitRepository.refresh(it) }
            },
            onSuccess = { resolved ->
                repoDir = resolved
                observeRepository(resolved)
                observeOperation(resolved)
            },
            onError = {
                updateState { copy(isRepository = false, loading = false, branch = "—") }
            },
        )
    }

    private fun observeOperation(repoDir: File) {
        tryToCollect(
            block = { operationMonitor.activeOperation(repoDir) },
            onCollect = { operation ->
                updateState {
                    copy(
                        isBusy = operation != null,
                        operationLabel = operation?.message ?: operation?.label,
                        operationProgress = operation?.progress,
                        operationCancellable = operation?.cancellable == true,
                    )
                }
            },
        )
    }

    private fun observeRepository(repoDir: File) {
        tryToCollect(
            block = { gitRepository.observeState(repoDir) },
            onCollect = { state ->
                val sections = state.toPanelSections()
                updateState {
                    val livePaths = buildSet {
                        sections.staged.forEach { add(it.path) }
                        sections.unstaged.forEach { add(it.path) }
                        sections.untracked.forEach { add(it.path) }
                        sections.conflicts.forEach { add(it.path) }
                    }
                    copy(
                        branch = state.branch.ifBlank { "—" },
                        isRepository = state.isRepository,
                        loading = false,
                        conflicts = sections.conflicts,
                        stagedChanges = sections.staged,
                        unstagedChanges = sections.unstaged,
                        untrackedChanges = sections.untracked,
                        commitMessage = state.commitMessage,
                        isCommitting = state.isCommitting,
                        ahead = state.ahead,
                        behind = state.behind,
                        repositoryState = state.repositoryState,
                        selectedPaths = selectedPaths.intersect(livePaths),
                    )
                }
            },
        )
    }

    override fun onSelectChange(path: String, target: GitDiffTarget) {
        val repoDir = repoDir ?: return
        updateState { copy(selectedPath = path, selectedDiffTarget = target) }
        tryToExecute(
            block = {
                when (target) {
                    GitDiffTarget.INDEX_TO_WORKTREE -> gitRepository.diffIndexToWorktree(repoDir, path)
                    GitDiffTarget.HEAD_TO_INDEX -> gitRepository.diffHeadToIndex(repoDir, path)
                    GitDiffTarget.COMMIT_TO_PARENT -> error("Commit diffs are opened from history")
                }
            },
            onSuccess = { diff ->
                val lines = diff.hunks.flatMap { it.lines }.map {
                    GitDiffLineUiModel(kind = it.kind.toAslDiffKind(), text = it.text, oldNo = it.oldNo, newNo = it.newNo)
                }
                updateState { copy(diffLines = lines) }
            },
        )
    }

    override fun onCloseDiff() {
        updateState { copy(selectedPath = null, diffLines = emptyList()) }
    }

    override fun onStage(path: String) {
        if (state.value.isBusy) return
        val repoDir = repoDir ?: return
        tryToExecute(block = { gitRepository.stage(repoDir, path) }, onError = ::showError)
    }

    override fun onUnstage(path: String) {
        if (state.value.isBusy) return
        val repoDir = repoDir ?: return
        tryToExecute(block = { gitRepository.unstage(repoDir, path) }, onError = ::showError)
    }

    override fun onStageAll() {
        if (state.value.isBusy) return
        val repoDir = repoDir ?: return
        tryToExecute(block = { gitRepository.stageAll(repoDir) }, onError = ::showError)
    }

    override fun onUnstageAll() {
        if (state.value.isBusy) return
        val repoDir = repoDir ?: return
        tryToExecute(block = { gitRepository.unstageAll(repoDir) }, onError = ::showError)
    }

    override fun onCommitMessageChanged(message: String) {
        updateState { copy(commitMessage = message) }
        val repoDir = repoDir ?: return
        tryToExecute(block = { gitRepository.setCommitMessage(repoDir, message) })
    }

    override fun onCommit() {
        if (!state.value.canCommit) return
        val repoDir = repoDir ?: return
        updateState { copy(isCommitting = true) }
        tryToExecute(
            block = {
                stageForCommit(repoDir)
                gitRepository.commit(repoDir, amend = false)
            },
            onSuccess = { id ->
                updateState {
                    copy(
                        isCommitting = false,
                        selectedPath = null,
                        diffLines = emptyList(),
                        statusMessage = "Committed ${id.take(7)}",
                    )
                }
            },
            onError = { updateState { copy(isCommitting = false, statusMessage = gitErrorMessage(it)) } },
        )
    }

    override fun onCommitAndPush() {
        if (!state.value.canCommit) return

        ensureRemoteAuth { commitAndPush() }
    }

    private suspend fun stageForCommit(repoDir: File) {
        val current = state.value
        if (current.stagedChanges.isNotEmpty()) return
        val unstagedPaths = (current.unstagedChanges + current.untrackedChanges).map { it.path }
        val selected = current.selectedPaths.filter { it in unstagedPaths }
        val toStage = selected.ifEmpty { unstagedPaths }
        toStage.forEach { gitRepository.stage(repoDir, it) }
    }

    private fun commitAndPush() {
        if (!state.value.canCommit) return
        val repoDir = repoDir ?: return
        var committedId: String? = null
        updateState { copy(isCommitting = true) }
        val job = tryToExecute(
            block = {
                stageForCommit(repoDir)
                val id = gitRepository.commit(repoDir, amend = false)
                committedId = id
                try {
                    gitRepository.push(repoDir)
                    id
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (pushError: Throwable) {
                    throw CommitSucceededPushFailed(id, pushError)
                }
            },
            onSuccess = { id ->
                updateState { copy(isCommitting = false, statusMessage = "Committed and pushed ${id.take(7)}") }
            },
            onError = { error ->
                val message = if (error is CommitSucceededPushFailed) {
                    "Committed ${error.commitId.take(7)}, but push failed: ${gitErrorMessage(error.cause ?: error)}"
                } else {
                    gitErrorMessage(error)
                }
                updateState { copy(isCommitting = false, statusMessage = message) }
            },
        )
        job.invokeOnCompletion { error ->
            if (error is CancellationException) {
                val id = committedId
                updateState {
                    copy(
                        isCommitting = false,
                        statusMessage = id?.let { "Committed ${it.take(7)}, but push was cancelled" }
                            ?: "Operation cancelled",
                    )
                }
            }
        }
    }

    override fun onOpenAuthorDialog() {
        val repoDir = repoDir ?: return
        tryToExecute(
            block = { gitRepository.getAuthorConfig(repoDir) },
            onSuccess = { config ->
                val shown = config.local ?: config.effective
                updateState { copy(authorDialogVisible = true, authorName = shown.name, authorEmail = shown.email) }
            },
            onError = ::showError,
        )
    }

    override fun onAuthorNameChanged(name: String) = updateState { copy(authorName = name) }
    override fun onAuthorEmailChanged(email: String) = updateState { copy(authorEmail = email) }

    override fun onSaveLocalAuthor() {
        val repoDir = repoDir ?: return
        val config = GitAuthorConfig(state.value.authorName.trim(), state.value.authorEmail.trim())
        if (config.name.isBlank() || config.email.isBlank()) return
        tryToExecute(
            block = { gitRepository.setLocalAuthor(repoDir, config) },
            onSuccess = { updateState { copy(authorDialogVisible = false, statusMessage = "Local Git author saved") } },
            onError = ::showError,
        )
    }

    override fun onUseAppAuthor() {
        val repoDir = repoDir ?: return
        tryToExecute(
            block = { gitRepository.setLocalAuthor(repoDir, null) },
            onSuccess = { updateState { copy(authorDialogVisible = false, statusMessage = "Using app Git author") } },
            onError = ::showError,
        )
    }

    override fun onDismissAuthorDialog() = updateState { copy(authorDialogVisible = false) }

    override fun onPush() {
        if (state.value.isBusy) return
        val repoDir = repoDir ?: return
        ensureRemoteAuth { sync("Push") { gitRepository.push(repoDir) } }
    }

    override fun onFetch() {
        if (state.value.isBusy) return
        val repoDir = repoDir ?: return
        ensureRemoteAuth { sync("Fetch") { gitRepository.fetch(repoDir) } }
    }

    override fun onPull() {
        if (state.value.isBusy) return
        val repoDir = repoDir ?: return
        val mode = state.value.pullMode
        ensureRemoteAuth { sync("Pull") { gitRepository.pull(repoDir, mode) } }
    }

    override fun onPullModeChanged(mode: PullMode) = updateState { copy(pullMode = mode) }

    override fun onCancelOperation() {
        val repoDir = repoDir ?: return
        operationMonitor.cancelActiveOperation(repoDir)
    }

    override fun onRequestForcePush() = updateState { copy(forcePushConfirmVisible = true) }

    override fun onConfirmForcePush() {
        updateState { copy(forcePushConfirmVisible = false) }
        val repoDir = repoDir ?: return
        ensureRemoteAuth { sync("Force push") { gitRepository.pushForceWithLease(repoDir) } }
    }

    override fun onDismissForcePush() = updateState { copy(forcePushConfirmVisible = false) }

    override fun onOpenRemotes() {
        updateState { copy(remotesVisible = true, remotesLoading = true) }
        reloadRemotes()
    }

    override fun onCloseRemotes() = updateState { copy(remotesVisible = false) }

    override fun onAddRemote() = updateState {
        copy(
            remoteEditorVisible = true,
            editingRemoteName = null,
            remoteName = "",
            remoteUrl = "",
            remoteNameError = null,
            remoteUrlError = null,
        )
    }

    override fun onEditRemote(name: String) {
        val remote = state.value.remotes.firstOrNull { it.name == name } ?: return
        updateState {
            copy(
                remoteEditorVisible = true,
                editingRemoteName = name,
                remoteName = name,
                remoteUrl = remote.url,
                remoteNameError = null,
                remoteUrlError = null,
            )
        }
    }

    override fun onRemoteNameChanged(name: String) = updateState {
        copy(remoteName = name, remoteNameError = null)
    }

    override fun onRemoteUrlChanged(url: String) = updateState {
        copy(remoteUrl = url, remoteUrlError = null)
    }

    override fun onSaveRemote() {
        val repoDir = repoDir ?: return
        val editing = state.value.editingRemoteName
        val url = state.value.remoteUrl.trim()


        val urlError = if (isSupportedRemoteUrl(url)) null else "Use an http(s):// or file:// URL"
        if (urlError != null) {
            updateState { copy(remoteUrlError = urlError) }
            return
        }
        val originExists = state.value.remotes.any { it.name == "origin" }
        tryToExecute(
            block = {
                when {
                    editing != null -> gitRepository.setRemoteUrl(repoDir, editing, url)
                    originExists -> gitRepository.setRemoteUrl(repoDir, "origin", url)
                    else -> gitRepository.addRemote(repoDir, "origin", url)
                }
            },
            onSuccess = {
                updateState { copy(remoteEditorVisible = false, statusMessage = "Remote saved") }
                reloadRemotes()
            },
            onError = ::showError,
        )
    }

    override fun onDismissRemoteEditor() = updateState { copy(remoteEditorVisible = false) }

    override fun onRequestRemoveRemote(name: String) = updateState { copy(pendingRemoteRemoval = name) }

    override fun onConfirmRemoveRemote() {
        val repoDir = repoDir ?: return
        val name = state.value.pendingRemoteRemoval ?: return
        tryToExecute(
            block = { gitRepository.removeRemote(repoDir, name) },
            onSuccess = {
                updateState { copy(pendingRemoteRemoval = null, statusMessage = "Removed $name") }
                reloadRemotes()
            },
            onError = ::showError,
        )
    }

    override fun onDismissRemoveRemote() = updateState { copy(pendingRemoteRemoval = null) }

    override fun onRefresh() {
        val repoDir = repoDir ?: return
        tryToExecute(block = { gitRepository.refresh(repoDir) }, onError = ::showError)
    }

    fun onAppForegrounded() {
        val repoDir = repoDir ?: return
        tryToExecute(block = { gitRepository.onAppForegrounded(repoDir) })
    }

    override fun onStatusMessageShown() {
        updateState { copy(statusMessage = null) }
    }

    override fun onContinueOperation() {
        val root = repoDir ?: return
        if (state.value.repositoryState != GitRepositoryState.REBASING) return
        tryToExecute(block = { gitRepository.rebaseContinue(root) }, onError = ::showError)
    }

    override fun onRequestAbortOperation() = updateState { copy(abortConfirmVisible = true) }

    override fun onConfirmAbortOperation() {
        val root = repoDir ?: return
        val repositoryState = state.value.repositoryState
        updateState { copy(abortConfirmVisible = false) }
        tryToExecute(
            block = {
                when (repositoryState) {
                    GitRepositoryState.MERGING -> gitRepository.mergeAbort(root)
                    GitRepositoryState.REBASING -> gitRepository.rebaseAbort(root)
                    GitRepositoryState.CHERRY_PICKING -> gitRepository.cherryPickAbort(root)
                    GitRepositoryState.REVERTING -> gitRepository.revertAbort(root)
                    GitRepositoryState.SAFE, GitRepositoryState.BISECTING -> Unit
                }
            },
            onError = ::showError,
        )
    }

    override fun onDismissAbortOperation() = updateState { copy(abortConfirmVisible = false) }

    override fun onRequestRestore(path: String) = updateState { copy(pendingRestorePaths = listOf(path)) }

    override fun onConfirmRestore() {
        val root = repoDir ?: return
        val paths = state.value.pendingRestorePaths
        updateState { copy(pendingRestorePaths = emptyList()) }
        tryToExecute(block = { gitRepository.restoreFiles(root, paths) }, onError = ::showError)
    }

    override fun onDismissRestore() = updateState { copy(pendingRestorePaths = emptyList()) }

    override fun onPreviewClean() {
        val root = repoDir ?: return
        tryToExecute(
            block = { gitRepository.clean(root, dryRun = true, includeIgnored = state.value.cleanIncludeIgnored) },
            onSuccess = { updateState { copy(cleanPreview = it) } },
            onError = ::showError,
        )
    }

    override fun onCleanIncludeIgnoredChanged(include: Boolean) {
        updateState { copy(cleanIncludeIgnored = include, cleanPreview = null) }
        onPreviewClean()
    }

    override fun onConfirmClean() {
        val root = repoDir ?: return
        val include = state.value.cleanIncludeIgnored
        tryToExecute(
            block = { gitRepository.clean(root, dryRun = false, includeIgnored = include) },
            onSuccess = { removed -> updateState { copy(cleanPreview = null, statusMessage = "Removed ${removed.size} path(s)") } },
            onError = ::showError,
        )
    }

    override fun onDismissClean() = updateState { copy(cleanPreview = null) }

    override fun onOpenSubmodules() {
        updateState { copy(submodulesVisible = true, submodulesLoading = true) }
        reloadSubmodules()
    }

    override fun onCloseSubmodules() = updateState { copy(submodulesVisible = false) }

    override fun onInitSubmodules() {
        val root = repoDir ?: return
        tryToExecute(
            block = { gitRepository.submoduleInit(root) },
            onSuccess = {
                updateState { copy(statusMessage = "Submodules initialised") }
                reloadSubmodules()
            },
            onError = ::showError,
        )
    }

    override fun onUpdateSubmodules() {
        val root = repoDir ?: return
        tryToExecute(
            block = { gitRepository.submoduleUpdate(root) },
            onSuccess = {
                updateState { copy(statusMessage = "Submodules updated") }
                reloadSubmodules()
            },
            onError = ::showError,
        )
    }

    override fun onOpenBootstrap() = updateState { copy(bootstrapVisible = true) }

    override fun onBootstrapInitialCommitChanged(enabled: Boolean) = updateState {
        copy(bootstrapInitialCommit = enabled)
    }

    override fun onBootstrapMessageChanged(message: String) = updateState { copy(bootstrapMessage = message) }

    override fun onConfirmBootstrap() {
        val root = repoDir ?: return
        val message = if (state.value.bootstrapInitialCommit) {
            state.value.bootstrapMessage.trim().ifBlank { "Initial commit" }
        } else {
            null
        }
        updateState { copy(bootstrapVisible = false) }
        tryToExecute(
            block = { gitRepository.bootstrapRepository(root, message) },
            onSuccess = {
                observeOperation(root)
                updateState { copy(statusMessage = "Version control enabled") }
            },
            onError = ::showError,
        )
    }

    override fun onDismissBootstrap() = updateState { copy(bootstrapVisible = false) }

    override fun onSelectAllChanges() = updateState { copy(selectedPaths = allChangePaths) }

    override fun onClearSelection() = updateState { copy(selectedPaths = emptySet()) }

    override fun onToggleSelect(path: String) = updateState {
        copy(selectedPaths = if (path in selectedPaths) selectedPaths - path else selectedPaths + path)
    }

    override fun onToggleSectionSelect(paths: List<String>, select: Boolean) = updateState {
        copy(selectedPaths = if (select) selectedPaths + paths else selectedPaths - paths.toSet())
    }

    override fun onStageSelected() {
        if (state.value.isBusy) return
        val repoDir = repoDir ?: return
        val paths = state.value.selectedPaths.filter { path ->
            state.value.unstagedChanges.any { it.path == path } ||
                state.value.untrackedChanges.any { it.path == path }
        }
        if (paths.isEmpty()) return
        tryToExecute(
            block = { paths.forEach { gitRepository.stage(repoDir, it) } },
            onSuccess = { updateState { copy(selectedPaths = emptySet()) } },
            onError = ::showError,
        )
    }

    override fun onUnstageSelected() {
        if (state.value.isBusy) return
        val repoDir = repoDir ?: return
        val paths = state.value.selectedPaths.filter { path ->
            state.value.stagedChanges.any { it.path == path }
        }
        if (paths.isEmpty()) return
        tryToExecute(
            block = { paths.forEach { gitRepository.unstage(repoDir, it) } },
            onSuccess = { updateState { copy(selectedPaths = emptySet()) } },
            onError = ::showError,
        )
    }

    override fun onRevertSelected() {
        val paths = state.value.revertableSelection()
        if (paths.isEmpty()) return
        updateState { copy(pendingRestorePaths = paths) }
    }


    override fun onAuthModeChanged(mode: GitAuthMode) = authController.onAuthModeChanged(mode)
    override fun onAuthTokenChanged(token: String) = authController.onAuthTokenChanged(token)
    override fun onSubmitAuthToken() = authController.onSubmitAuthToken()
    override fun onStartGitHubSignIn() = authController.onStartGitHubSignIn()
    override fun onDismissAuthPrompt() = authController.onDismissAuthPrompt()

    private fun showError(error: Throwable) = updateState { copy(statusMessage = gitErrorMessage(error)) }

    private fun ensureRemoteAuth(run: () -> Unit) {
        if (state.value.isSyncing || state.value.isBusy) return
        tryToExecute(
            block = { resolveRemoteHost() },
            onSuccess = { host ->
                if (host == null || authController.hasCredentials(host)) run()
                else authController.open(host, retry = run)
            },
            onError = { run() },
        )
    }

    private suspend fun resolveRemoteHost(): String? {
        val dir = repoDir ?: return null
        val remotes = gitRepository.listRemotes(dir)
        val url = (remotes.firstOrNull { it.name == "origin" } ?: remotes.firstOrNull())?.url ?: return null
        return runCatching { URI(url.trim()).host }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun sync(label: String, block: suspend () -> com.ahmadkharfan.androidstudiolite.domain.model.GitSyncResult) {
        if (state.value.isSyncing || state.value.isBusy) return
        updateState { copy(isSyncing = true) }
        syncJob = tryToExecute(
            block = block,
            onSuccess = { result ->
                updateState { copy(isSyncing = false, statusMessage = "$label: ${result.detail}") }
            },
            onError = { error ->
                updateState { copy(isSyncing = false) }
                if (error is GitException.Auth) {

                    tryToExecute(
                        block = { resolveRemoteHost() },
                        onSuccess = { host -> authController.open(host) { sync(label, block) } },
                        onError = { authController.open(null) { sync(label, block) } },
                    )
                } else {
                    updateState { copy(statusMessage = gitErrorMessage(error)) }
                }
            },
        )
        syncJob?.invokeOnCompletion { error ->
            if (error is CancellationException) {
                updateState { copy(isSyncing = false, statusMessage = "Operation cancelled") }
            }
        }
    }

    private fun reloadRemotes() {
        val repoDir = repoDir ?: return
        tryToExecute(
            block = { gitRepository.listRemotes(repoDir) },
            onSuccess = { remotes -> updateState { copy(remotes = remotes, remotesLoading = false) } },
            onError = {
                updateState { copy(remotesLoading = false) }
                showError(it)
            },
        )
    }

    private fun reloadSubmodules() {
        val root = repoDir ?: return
        tryToExecute(
            block = { gitRepository.submodules(root) },
            onSuccess = { updateState { copy(submodules = it, submodulesLoading = false) } },
            onError = {
                updateState { copy(submodulesLoading = false) }
                showError(it)
            },
        )
    }

    private fun isSupportedRemoteUrl(value: String): Boolean {
        val normalized = value.lowercase()
        if (SUPPORTED_REMOTE_PREFIXES.none(normalized::startsWith)) return false
        return runCatching { URI(value).scheme?.lowercase() in SUPPORTED_REMOTE_SCHEMES }.getOrDefault(false)
    }

    private fun GitDiffKind.toAslDiffKind(): AslDiffKind = when (this) {
        GitDiffKind.ADDED -> AslDiffKind.Added
        GitDiffKind.REMOVED -> AslDiffKind.Removed
        GitDiffKind.MODIFIED -> AslDiffKind.Modified
        GitDiffKind.CONTEXT -> AslDiffKind.Context
    }

    private companion object {
        val SUPPORTED_REMOTE_SCHEMES = setOf("http", "https", "file")
        val SUPPORTED_REMOTE_PREFIXES = listOf("http://", "https://", "file://")
    }
}

private class CommitSucceededPushFailed(val commitId: String, cause: Throwable) : Exception(cause)
