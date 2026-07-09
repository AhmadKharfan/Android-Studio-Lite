package com.example.androidstudiolite.feature.editor.git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.designsystem.component.content.AslDiffKind
import com.example.androidstudiolite.domain.model.GitDiffKind
import com.example.androidstudiolite.domain.repository.GitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GitPanelViewModel(
    private val gitRepository: GitRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GitPanelUiState())
    val uiState: StateFlow<GitPanelUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            gitRepository.observeState().collect { state ->
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
            is GitPanelInteraction.CommitMessageChanged -> viewModelScope.launch { gitRepository.setCommitMessage(interaction.message) }
            GitPanelInteraction.Commit -> viewModelScope.launch {
                gitRepository.commit()
                _uiState.update { it.copy(selectedPath = null, diffLines = emptyList()) }
            }
        }
    }

    private fun selectChange(path: String) {
        _uiState.update { it.copy(selectedPath = path) }
        viewModelScope.launch {
            val lines = gitRepository.getDiff(path).map {
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
