package com.ahmadkharfan.androidstudiolite.feature.editor

import androidx.compose.runtime.Immutable
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffTarget

@Immutable
data class EditorNavigation(
    val onCloseProject: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenAiAgentSettings: () -> Unit,
    val onOpenGitDiff: (String, GitDiffTarget) -> Unit,
    val onOpenGitHistory: (String) -> Unit,
    val onOpenGitBlame: (String) -> Unit,
    val onOpenBranches: () -> Unit,
    val onOpenTags: () -> Unit,
    val onOpenStashes: () -> Unit,
    val onOpenHistory: () -> Unit,
    val onOpenConflicts: () -> Unit,
)
