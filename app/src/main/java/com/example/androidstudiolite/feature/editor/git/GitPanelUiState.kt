package com.example.androidstudiolite.feature.editor.git
import androidx.compose.runtime.Immutable

import com.example.androidstudiolite.designsystem.component.content.AslDiffKind
import com.example.androidstudiolite.domain.model.GitFileStatus

@Immutable
data class GitChangeUiModel(val path: String, val status: GitFileStatus)

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
    val changes: List<GitChangeUiModel> = emptyList(),
    val selectedPath: String? = null,
    val diffLines: List<GitDiffLineUiModel> = emptyList(),
    val commitMessage: String = "",
    val committing: Boolean = false,
)
