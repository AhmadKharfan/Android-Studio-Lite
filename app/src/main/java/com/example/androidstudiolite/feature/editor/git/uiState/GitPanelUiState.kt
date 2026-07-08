package com.example.androidstudiolite.feature.editor.git.uiState

import com.example.androidstudiolite.core.designsystem.component.content.AslDiffKind
import com.example.androidstudiolite.domain.model.GitFileStatus

data class GitChangeUiModel(val path: String, val status: GitFileStatus)

data class GitDiffLineUiModel(
    val kind: AslDiffKind,
    val text: String,
    val oldNo: Int?,
    val newNo: Int?,
)

data class GitPanelUiState(
    val branch: String = "main",
    val changes: List<GitChangeUiModel> = emptyList(),
    val selectedPath: String? = null,
    val diffLines: List<GitDiffLineUiModel> = emptyList(),
    val commitMessage: String = "",
    val committing: Boolean = false,
)
