package com.example.androidstudiolite.feature.editor.git

import com.example.androidstudiolite.core.BaseViewModel
import com.example.androidstudiolite.designsystem.component.content.AslDiffKind
import com.example.androidstudiolite.domain.model.GitDiffKind
import com.example.androidstudiolite.domain.repository.GitRepository

class GitPanelViewModel(
    private val gitRepository: GitRepository,
) : BaseViewModel<GitPanelUiState, Nothing>(
    initialState = GitPanelUiState(),
), GitPanelInteractionListener {

    init {
        tryToCollect(
            block = { gitRepository.observeState() },
            onCollect = { state ->
                updateState {
                    copy(
                        branch = state.branch,
                        changes = state.changes.map { change -> GitChangeUiModel(change.path, change.status) },
                        commitMessage = state.commitMessage,
                        committing = state.committing,
                    )
                }
            },
        )
    }

    override fun onSelectChange(path: String) {
        updateState { copy(selectedPath = path) }
        tryToExecute(
            block = { gitRepository.getDiff(path) },
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

    override fun onCommitMessageChanged(message: String) {
        tryToExecute(block = { gitRepository.setCommitMessage(message) })
    }

    override fun onCommit() {
        tryToExecute(
            block = { gitRepository.commit() },
            onSuccess = { updateState { copy(selectedPath = null, diffLines = emptyList()) } },
        )
    }

    private fun GitDiffKind.toAslDiffKind(): AslDiffKind = when (this) {
        GitDiffKind.ADDED -> AslDiffKind.Added
        GitDiffKind.REMOVED -> AslDiffKind.Removed
        GitDiffKind.MODIFIED -> AslDiffKind.Modified
        GitDiffKind.CONTEXT -> AslDiffKind.Context
    }
}
