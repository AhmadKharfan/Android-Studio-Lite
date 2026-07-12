package com.ahmadkharfan.androidstudiolite.feature.editor.git

import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslDiffKind
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffKind
import com.ahmadkharfan.androidstudiolite.domain.repository.GitRepository
import java.io.File

/**
 * Drives the in-editor git panel against the open project's working tree ([repoDir]). All git work is
 * delegated to [gitRepository], which runs it off the main thread.
 */
class GitPanelViewModel(
    private val repoDir: File,
    private val gitRepository: GitRepository,
) : BaseViewModel<GitPanelUiState, Nothing>(
    initialState = GitPanelUiState(),
), GitPanelInteractionListener {

    init {
        tryToExecute(block = { gitRepository.refresh(repoDir) })
        tryToCollect(
            block = { gitRepository.observeState(repoDir) },
            onCollect = { state ->
                updateState {
                    copy(
                        branch = state.branch.ifBlank { "—" },
                        isRepository = state.isRepository,
                        changes = state.changes.map { GitChangeUiModel(it.path, it.status, it.staged) },
                        commitMessage = state.commitMessage,
                        committing = state.committing,
                        ahead = state.ahead,
                        behind = state.behind,
                    )
                }
            },
        )
    }

    override fun onSelectChange(path: String) {
        updateState { copy(selectedPath = path) }
        tryToExecute(
            block = { gitRepository.getDiff(repoDir, path) },
            onSuccess = { diff ->
                val lines = diff.map {
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
        tryToExecute(block = { gitRepository.stage(repoDir, path) })
    }

    override fun onUnstage(path: String) {
        tryToExecute(block = { gitRepository.unstage(repoDir, path) })
    }

    override fun onCommitMessageChanged(message: String) {
        updateState { copy(commitMessage = message) }
        tryToExecute(block = { gitRepository.setCommitMessage(repoDir, message) })
    }

    override fun onCommit() {
        if (!state.value.canCommit) return
        updateState { copy(committing = true) }
        tryToExecute(
            block = { gitRepository.commit(repoDir) },
            onSuccess = { id ->
                updateState {
                    copy(
                        committing = false,
                        selectedPath = null,
                        diffLines = emptyList(),
                        statusMessage = "Committed ${id.take(7)}",
                    )
                }
            },
            onError = { updateState { copy(committing = false, statusMessage = it.message ?: "Commit failed") } },
        )
    }

    override fun onPush() = sync("Push") { gitRepository.push(repoDir) }

    override fun onPull() = sync("Pull") { gitRepository.pull(repoDir) }

    override fun onRefresh() {
        tryToExecute(block = { gitRepository.refresh(repoDir) })
    }

    override fun onStatusMessageShown() {
        updateState { copy(statusMessage = null) }
    }

    private fun sync(label: String, block: suspend () -> com.ahmadkharfan.androidstudiolite.domain.model.GitSyncResult) {
        if (state.value.syncing) return
        updateState { copy(syncing = true) }
        tryToExecute(
            block = block,
            onSuccess = { result ->
                updateState { copy(syncing = false, statusMessage = "$label: ${result.detail}") }
            },
            onError = { updateState { copy(syncing = false, statusMessage = "$label failed: ${it.message.orEmpty()}") } },
        )
    }

    private fun GitDiffKind.toAslDiffKind(): AslDiffKind = when (this) {
        GitDiffKind.ADDED -> AslDiffKind.Added
        GitDiffKind.REMOVED -> AslDiffKind.Removed
        GitDiffKind.MODIFIED -> AslDiffKind.Modified
        GitDiffKind.CONTEXT -> AslDiffKind.Context
    }
}
