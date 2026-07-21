package com.ahmadkharfan.androidstudiolite.feature.editor.git.refs

import androidx.lifecycle.viewModelScope
import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.domain.model.GitBranch
import com.ahmadkharfan.androidstudiolite.domain.model.GitException
import com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitStash
import com.ahmadkharfan.androidstudiolite.domain.model.GitTag
import com.ahmadkharfan.androidstudiolite.domain.model.PullMode
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubDeviceAuthenticator
import com.ahmadkharfan.androidstudiolite.domain.repository.GitRepository
import com.ahmadkharfan.androidstudiolite.domain.usecase.ProjectPathResolver
import com.ahmadkharfan.androidstudiolite.feature.editor.git.GitAuthController
import com.ahmadkharfan.androidstudiolite.feature.editor.git.GitAuthMode
import com.ahmadkharfan.androidstudiolite.feature.editor.git.GitAuthPromptState
import com.ahmadkharfan.androidstudiolite.feature.git.gitErrorMessage
import java.io.File
import java.net.URI

enum class GitRefsMode { BRANCHES, TAGS, STASHES }

data class GitRefsUiState(
    val mode: GitRefsMode,
    val branches: List<GitBranch> = emptyList(),
    val tags: List<GitTag> = emptyList(),
    val stashes: List<GitStash> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val forceDeleteCandidate: String? = null,
    val syncMessage: String? = null,
    val isSyncing: Boolean = false,
    val ahead: Int? = null,
    val behind: Int? = null,
    val authPrompt: GitAuthPromptState = GitAuthPromptState(),
)

class GitRefsViewModel(
    projectId: String,
    private val mode: GitRefsMode,
    projectPathResolver: ProjectPathResolver,
    private val gitRepository: GitRepository,
    private val credentialStore: GitCredentialStore,
    private val authenticator: GitHubDeviceAuthenticator,
) : BaseViewModel<GitRefsUiState, Nothing>(
    GitRefsUiState(mode, authPrompt = GitAuthPromptState(gitHubAvailable = authenticator.isConfigured)),
), GitRefsInteractionListener {
    private data class LoadedRefs(
        val branches: List<GitBranch> = emptyList(),
        val tags: List<GitTag> = emptyList(),
        val stashes: List<GitStash> = emptyList(),
    )

    private var repoDir: File? = null

    private val authController = GitAuthController(
        scope = viewModelScope,
        credentialStore = credentialStore,
        authenticator = authenticator,
        emit = { prompt -> updateState { copy(authPrompt = prompt) } },
    )

    init {
        tryToExecute(
            block = { projectPathResolver(projectId) },
            onSuccess = { root ->
                repoDir = root
                refresh()
                if (mode == GitRefsMode.BRANCHES) {
                    tryToCollect(
                        block = { gitRepository.observeState(root) },
                        onCollect = { state -> updateState { copy(ahead = state.ahead, behind = state.behind) } },
                    )
                }
            },
            onError = { updateState { copy(loading = false, error = gitErrorMessage(it)) } },
        )
    }

    fun refresh() {
        val root = repoDir ?: return
        updateState { copy(loading = true, error = null) }
        tryToExecute(
            block = {
                when (mode) {
                    GitRefsMode.BRANCHES -> LoadedRefs(branches = gitRepository.branches(root))
                    GitRefsMode.TAGS -> LoadedRefs(tags = gitRepository.listTags(root))
                    GitRefsMode.STASHES -> LoadedRefs(stashes = gitRepository.stashList(root))
                }
            },
            onSuccess = { loaded ->
                updateState {
                    copy(
                        branches = loaded.branches,
                        tags = loaded.tags,
                        stashes = loaded.stashes,
                        loading = false,
                    )
                }
            },
            onError = { updateState { copy(loading = false, error = gitErrorMessage(it)) } },
        )
    }

    override fun checkout(branch: GitBranch) = runMutation {
        if (branch.isRemote) gitRepository.checkoutRemoteBranch(requireRoot(), branch.name)
        else gitRepository.checkout(requireRoot(), branch.name)
    }

    override fun createBranch(name: String) = runMutation { gitRepository.createBranch(requireRoot(), name.trim()) }

    override fun renameBranch(oldName: String, newName: String) = runMutation {
        gitRepository.renameBranch(requireRoot(), oldName, newName.trim())
    }

    override fun deleteBranch(name: String, force: Boolean) = runMutation(
        block = { gitRepository.deleteBranch(requireRoot(), name, force) },
        onError = { error ->
            updateState {
                copy(
                    loading = false,
                    forceDeleteCandidate = name.takeIf { error is GitException.BranchNotMerged },
                    error = gitErrorMessage(error),
                )
            }
        },
    )

    override fun dismissForceDelete() = updateState { copy(forceDeleteCandidate = null) }

    override fun publish(name: String) {
        ensureRemoteAuth { syncOp(retry = { publish(name) }) { gitRepository.publishBranch(requireRoot(), name).detail } }
    }

    override fun merge(name: String) {
        updateState { copy(loading = true, error = null, syncMessage = null) }
        tryToExecute(
            block = { gitRepository.merge(requireRoot(), name) },
            onSuccess = { result ->
                updateState { copy(loading = false, syncMessage = "Merge ${name.integrationMessage(result.status)}") }
                refresh()
            },
            onError = { updateState { copy(loading = false, error = gitErrorMessage(it)) } },
        )
    }

    override fun fetch() {
        ensureRemoteAuth { syncOp(retry = ::fetch) { gitRepository.fetch(requireRoot()).detail } }
    }

    override fun pull(mode: PullMode) {
        ensureRemoteAuth { syncOp(retry = { pull(mode) }) { gitRepository.pull(requireRoot(), mode).detail } }
    }

    override fun push() {
        ensureRemoteAuth { syncOp(retry = ::push) { gitRepository.push(requireRoot()).detail } }
    }

    private fun syncOp(retry: () -> Unit, block: suspend () -> String) {
        if (state.value.isSyncing) return
        updateState { copy(isSyncing = true, error = null, syncMessage = null) }
        tryToExecute(
            block = block,
            onSuccess = { detail -> updateState { copy(isSyncing = false, syncMessage = detail) }; refresh() },
            onError = { error ->
                updateState { copy(isSyncing = false) }
                if (error is GitException.Auth) promptForAuth(retry)
                else updateState { copy(error = gitErrorMessage(error)) }
            },
        )
    }

    private fun ensureRemoteAuth(run: () -> Unit) {
        if (state.value.isSyncing) return
        tryToExecute(
            block = { resolveRemoteHost() },
            onSuccess = { host ->
                if (host == null || authController.hasCredentials(host)) run()
                else authController.open(host, retry = run)
            },
            onError = { run() },
        )
    }

    private fun promptForAuth(retry: () -> Unit) {
        tryToExecute(
            block = { resolveRemoteHost() },
            onSuccess = { host -> authController.open(host, retry) },
            onError = { authController.open(null, retry) },
        )
    }

    private suspend fun resolveRemoteHost(): String? {
        val remotes = gitRepository.listRemotes(requireRoot())
        val url = (remotes.firstOrNull { it.name == "origin" } ?: remotes.firstOrNull())?.url ?: return null
        return runCatching { URI(url.trim()).host }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    override fun onAuthModeChanged(mode: GitAuthMode) = authController.onAuthModeChanged(mode)
    override fun onAuthTokenChanged(token: String) = authController.onAuthTokenChanged(token)
    override fun onSubmitAuthToken() = authController.onSubmitAuthToken()
    override fun onStartGitHubSignIn() = authController.onStartGitHubSignIn()
    override fun onDismissAuthPrompt() = authController.onDismissAuthPrompt()

    override fun dismissSyncMessage() = updateState { copy(syncMessage = null) }

    override fun createTag(name: String, message: String?) = runMutation {
        gitRepository.createTag(requireRoot(), name.trim(), message?.takeIf { it.isNotBlank() })
    }

    override fun deleteTag(name: String) = runMutation { gitRepository.deleteTag(requireRoot(), name) }

    override fun pushTag(name: String) = ensureRemoteAuth {
        runMutation { gitRepository.pushTag(requireRoot(), name) }
    }

    override fun pushAllTags() = ensureRemoteAuth {
        runMutation { gitRepository.pushAllTags(requireRoot()) }
    }

    override fun createStash(message: String?, includeUntracked: Boolean) = runMutation {
        gitRepository.stashCreate(requireRoot(), message?.takeIf { it.isNotBlank() }, includeUntracked)
    }

    override fun applyStash(index: Int) = runMutation { gitRepository.stashApply(requireRoot(), index) }

    override fun popStash(index: Int) = runMutation { gitRepository.stashPop(requireRoot(), index) }

    override fun dropStash(index: Int) = runMutation { gitRepository.stashDrop(requireRoot(), index) }

    private fun runMutation(
        onError: (Throwable) -> Unit = { updateState { copy(loading = false, error = gitErrorMessage(it)) } },
        block: suspend () -> Any?,
    ) {
        updateState { copy(loading = true, error = null, forceDeleteCandidate = null) }
        tryToExecute(
            block = block,
            onSuccess = { refresh() },
            onError = onError,
        )
    }

    private fun requireRoot(): File = checkNotNull(repoDir)
}

private fun String.integrationMessage(status: GitIntegrationStatus): String = when (status) {
    GitIntegrationStatus.FAST_FORWARD -> "fast-forwarded current branch to $this"
    GitIntegrationStatus.MERGED -> "merged $this into the current branch"
    GitIntegrationStatus.ALREADY_UP_TO_DATE -> "already up to date with $this"
    GitIntegrationStatus.CONFLICTS -> "produced conflicts. Resolve them in the Changes panel."
    GitIntegrationStatus.ABORTED_FF_ONLY -> "aborted: $this can't be fast-forwarded"
    GitIntegrationStatus.APPLIED, GitIntegrationStatus.ABORTED -> "finished ($status)"
}
