package com.ahmadkharfan.androidstudiolite.feature.editor.api

import androidx.compose.runtime.Immutable
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffTarget

@Immutable
public data class EditorNavigation(
    public val onCloseProject: () -> Unit,
    public val onOpenSettings: () -> Unit,
    public val onOpenAiAgentSettings: () -> Unit,
    public val onOpenGitDiff: (String, GitDiffTarget) -> Unit,
    public val onOpenGitHistory: (String) -> Unit,
    public val onOpenGitBlame: (String) -> Unit,
    public val onOpenBranches: () -> Unit,
    public val onOpenTags: () -> Unit,
    public val onOpenStashes: () -> Unit,
    public val onOpenHistory: () -> Unit,
    public val onOpenConflicts: () -> Unit,
)
