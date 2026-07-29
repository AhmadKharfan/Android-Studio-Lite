package com.ahmadkharfan.androidstudiolite.feature.editor.git

import androidx.compose.runtime.Composable
import com.ahmadkharfan.androidstudiolite.feature.git.api.GitPanelApi
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffTarget

class GitPanelApiImpl : GitPanelApi {
    @Composable
    override fun Panel(
        projectId: String,
        onClose: () -> Unit,
        onOpenDiff: (String, GitDiffTarget) -> Unit,
        onOpenHistory: () -> Unit,
        onOpenBranches: () -> Unit,
        onOpenTags: () -> Unit,
        onOpenStashes: () -> Unit,
        onOpenConflicts: () -> Unit,
    ) {
        GitPanelRoute(
            projectId = projectId,
            onClose = onClose,
            onOpenDiff = onOpenDiff,
            onOpenHistory = onOpenHistory,
            onOpenBranches = onOpenBranches,
            onOpenTags = onOpenTags,
            onOpenStashes = onOpenStashes,
            onOpenConflicts = onOpenConflicts,
        )
    }
}
