package com.ahmadkharfan.androidstudiolite.feature.git.api

import androidx.compose.runtime.Composable
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffTarget

/**
 * The git panel as other features see it: a composable to host, and callbacks for the destinations
 * it wants opened. The host decides where those destinations live, so no consumer needs to know how
 * git is implemented.
 */
interface GitPanelApi {
    @Composable
    fun Panel(
        projectId: String,
        onClose: () -> Unit,
        onOpenDiff: (String, GitDiffTarget) -> Unit = { _, _ -> },
        onOpenHistory: () -> Unit = {},
        onOpenBranches: () -> Unit = {},
        onOpenTags: () -> Unit = {},
        onOpenStashes: () -> Unit = {},
        onOpenConflicts: () -> Unit = {},
    )
}
