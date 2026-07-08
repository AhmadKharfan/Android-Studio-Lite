package com.example.androidstudiolite.feature.editor.git.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.core.designsystem.component.content.AslDiffKind
import com.example.androidstudiolite.domain.model.GitDiffKind
import com.example.androidstudiolite.domain.usecase.CommitGitChangesUseCase
import com.example.androidstudiolite.domain.usecase.GetGitDiffUseCase
import com.example.androidstudiolite.domain.usecase.ObserveGitStateUseCase
import com.example.androidstudiolite.domain.usecase.SetGitCommitMessageUseCase
import com.example.androidstudiolite.feature.editor.git.interaction.GitPanelInteraction
import com.example.androidstudiolite.feature.editor.git.uiState.GitChangeUiModel
import com.example.androidstudiolite.feature.editor.git.uiState.GitDiffLineUiModel
import com.example.androidstudiolite.feature.editor.git.uiState.GitPanelUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GitPanelViewModel(
    private val observeState: ObserveGitStateUseCase,
    private val getDiff: GetGitDiffUseCase,
    private val setCommitMessage: SetGitCommitMessageUseCase,
    private val commitChanges: CommitGitChangesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GitPanelUiState())
    val uiState: StateFlow<GitPanelUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeState().collect { state ->
                _uiState.update {
                    it.copy(
                        branch = state.branch,
                        changes = state.changes.map { change -> GitChangeUiModel(change.path, change.status) },
                        commitMessage = state.commitMessage,
                        committing = state.committing,
                    )
                }
            }
        }
    }

    fun onInteraction(interaction: GitPanelInteraction) {
        when (interaction) {
            is GitPanelInteraction.SelectChange -> selectChange(interaction.path)
            GitPanelInteraction.CloseDiff -> _uiState.update { it.copy(selectedPath = null, diffLines = emptyList()) }
            is GitPanelInteraction.CommitMessageChanged -> viewModelScope.launch { setCommitMessage(interaction.message) }
            GitPanelInteraction.Commit -> viewModelScope.launch {
                commitChanges()
                _uiState.update { it.copy(selectedPath = null, diffLines = emptyList()) }
            }
        }
    }

    private fun selectChange(path: String) {
        _uiState.update { it.copy(selectedPath = path) }
        viewModelScope.launch {
            val lines = getDiff(path).map {
                GitDiffLineUiModel(kind = it.kind.toAslDiffKind(), text = it.text, oldNo = it.oldNo, newNo = it.newNo)
            }
            _uiState.update { it.copy(diffLines = lines) }
        }
    }

    private fun GitDiffKind.toAslDiffKind(): AslDiffKind = when (this) {
        GitDiffKind.ADDED -> AslDiffKind.Added
        GitDiffKind.REMOVED -> AslDiffKind.Removed
        GitDiffKind.MODIFIED -> AslDiffKind.Modified
        GitDiffKind.CONTEXT -> AslDiffKind.Context
    }
}
