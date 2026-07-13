package com.ahmadkharfan.androidstudiolite.feature.editor.git
import androidx.compose.runtime.Immutable

import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslDiffKind
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileStatus

@Immutable
data class GitChangeUiModel(
    val path: String,
    val status: GitFileStatus,
    val staged: Boolean = false,
)

@Immutable
data class GitDiffLineUiModel(
    val kind: AslDiffKind,
    val text: String,
    val oldNo: Int?,
    val newNo: Int?,
)

@Immutable
data class GitPanelUiState(
    val branch: String = "main",
    val isRepository: Boolean = true,
    val changes: List<GitChangeUiModel> = emptyList(),
    val selectedPath: String? = null,
    val diffLines: List<GitDiffLineUiModel> = emptyList(),
    val commitMessage: String = "",
    val committing: Boolean = false,
    val ahead: Int? = null,
    val behind: Int? = null,
    val syncing: Boolean = false,
    val statusMessage: String? = null,
) {
    val stagedChanges: List<GitChangeUiModel> get() = changes.filter { it.staged }
    val unstagedChanges: List<GitChangeUiModel> get() = changes.filter { !it.staged }
    val canCommit: Boolean get() = commitMessage.isNotBlank() && changes.isNotEmpty() && !committing
}
