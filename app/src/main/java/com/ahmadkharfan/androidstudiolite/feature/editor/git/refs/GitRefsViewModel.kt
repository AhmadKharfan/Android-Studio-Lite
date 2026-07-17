package com.ahmadkharfan.androidstudiolite.feature.editor.git.refs

import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.domain.model.GitBranch
import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.model.GitException
import com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitStash
import com.ahmadkharfan.androidstudiolite.domain.model.GitTag
import com.ahmadkharfan.androidstudiolite.domain.model.PullMode
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import com.ahmadkharfan.androidstudiolite.domain.repository.GitRepository
import com.ahmadkharfan.androidstudiolite.domain.usecase.ProjectPathResolver
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
    /** Result banner for the current-branch sync actions (fetch/pull/push/merge); auto-dismissed. */
    val syncMessage: String? = null,
    val syncing: Boolean = false,
    val ahead: Int? = null,
    val behind: Int? = null,
    val authPromptVisible: Boolean = false,
    val authPromptToken: String = "",
    val authPromptHost: String? = null,
)

class GitRefsViewModel(
    projectId: String,
    private val mode: GitRefsMode,
    projectPathResolver: ProjectPathResolver,
    private val gitRepository: GitRepository,
    private val credentialStore: GitCredentialStore,
) : BaseViewModel<GitRefsUiState, Nothing>(GitRefsUiState(mode)) {
    private data class LoadedRefs(
        val branches: List<GitBranch> = emptyList(),
        val tags: List<GitTag> = emptyList(),
        val stashes: List<GitStash> = emptyList(),
    )

    private var repoDir: File? = null

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

    fun checkout(branch: GitBranch) = runMutation {
        if (branch.isRemote) gitRepository.checkoutRemoteBranch(requireRoot(), branch.name)
        else gitRepository.checkout(requireRoot(), branch.name)
    }

    fun createBranch(name: String) = runMutation { gitRepository.createBranch(requireRoot(), name.trim()) }

    fun renameBranch(oldName: String, newName: String) = runMutation {
        gitRepository.renameBranch(requireRoot(), oldName, newName.trim())
    }

    fun deleteBranch(name: String, force: Boolean = false) = runMutation(
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

    fun dismissForceDelete() = updateState { copy(forceDeleteCandidate = null) }

    fun publish(name: String) {
        syncOp(retry = { publish(name) }) { gitRepository.publishBranch(requireRoot(), name).detail }
    }

    /** Merges [name] into the current branch. Conflicts leave the working tree for the Changes panel. */
    fun merge(name: String) {
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

    fun fetch() {
        syncOp(retry = ::fetch) { gitRepository.fetch(requireRoot()).detail }
    }

    fun pull(mode: PullMode) {
        syncOp(retry = { pull(mode) }) { gitRepository.pull(requireRoot(), mode).detail }
    }

    fun push() {
        syncOp(retry = ::push) { gitRepository.push(requireRoot()).detail }
    }

    /** Runs a network sync, showing the token prompt (then retrying) when it fails with an auth error. */
    private fun syncOp(retry: () -> Unit, block: suspend () -> String) {
        if (state.value.syncing) return
        updateState { copy(syncing = true, error = null, syncMessage = null) }
        tryToExecute(
            block = block,
            onSuccess = { detail -> updateState { copy(syncing = false, syncMessage = detail) }; refresh() },
            onError = { error ->
                updateState { copy(syncing = false) }
                if (error is GitException.Auth) promptForAuth(retry)
                else updateState { copy(error = gitErrorMessage(error)) }
            },
        )
    }

    private var pendingAuthRetry: (() -> Unit)? = null

    private fun promptForAuth(retry: () -> Unit) {
        pendingAuthRetry = retry
        tryToExecute(
            block = {
                val remotes = gitRepository.listRemotes(requireRoot())
                (remotes.firstOrNull { it.name == "origin" } ?: remotes.firstOrNull())?.url
                    ?.let { runCatching { URI(it.trim()).host }.getOrNull() }?.takeIf { it.isNotBlank() }
            },
            onSuccess = { host -> updateState { copy(authPromptVisible = true, authPromptToken = "", authPromptHost = host) } },
            onError = { updateState { copy(authPromptVisible = true, authPromptToken = "", authPromptHost = null) } },
        )
    }

    fun onAuthTokenChanged(token: String) = updateState { copy(authPromptToken = token) }

    fun submitAuthToken() {
        val token = state.value.authPromptToken.trim()
        val host = state.value.authPromptHost
        if (token.isNotBlank() && host != null) credentialStore.save(host, GitCredentials(username = "", token = token))
        updateState { copy(authPromptVisible = false, authPromptToken = "") }
        val retry = pendingAuthRetry
        pendingAuthRetry = null
        if (token.isNotBlank()) retry?.invoke()
    }

    fun dismissAuthPrompt() {
        pendingAuthRetry = null
        updateState { copy(authPromptVisible = false, authPromptToken = "") }
    }

    fun dismissSyncMessage() = updateState { copy(syncMessage = null) }

    fun createTag(name: String, message: String?) = runMutation {
        gitRepository.createTag(requireRoot(), name.trim(), message?.takeIf { it.isNotBlank() })
    }

    fun deleteTag(name: String) = runMutation { gitRepository.deleteTag(requireRoot(), name) }

    fun pushTag(name: String) = runMutation { gitRepository.pushTag(requireRoot(), name) }

    fun pushAllTags() = runMutation { gitRepository.pushAllTags(requireRoot()) }

    fun createStash(message: String?, includeUntracked: Boolean) = runMutation {
        gitRepository.stashCreate(requireRoot(), message?.takeIf { it.isNotBlank() }, includeUntracked)
    }

    fun applyStash(index: Int) = runMutation { gitRepository.stashApply(requireRoot(), index) }

    fun popStash(index: Int) = runMutation { gitRepository.stashPop(requireRoot(), index) }

    fun dropStash(index: Int) = runMutation { gitRepository.stashDrop(requireRoot(), index) }

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
    GitIntegrationStatus.CONFLICTS -> "produced conflicts — resolve them in the Changes panel"
    GitIntegrationStatus.ABORTED_FF_ONLY -> "aborted: $this can't be fast-forwarded"
    GitIntegrationStatus.APPLIED, GitIntegrationStatus.ABORTED -> "finished ($status)"
}
