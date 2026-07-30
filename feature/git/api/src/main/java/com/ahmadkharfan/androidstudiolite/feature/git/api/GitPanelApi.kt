package com.ahmadkharfan.androidstudiolite.feature.git.api

import androidx.compose.runtime.Composable
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffTarget

/**
 * The git panel as other features see it: a composable to host, and callbacks for the destinations
 * it wants opened. The host decides where those destinations live, so no consumer needs to know how
 * git is implemented.
 */
interface GitPanelApi {

    /**
     * No default argument values here, deliberately. A `@Composable` interface member with defaults
     * makes the Compose compiler emit a `ComposeDefaultImpls.Panel$default` bridge whose expected
     * abstract signature does not line up with an implementation compiled in a *different* module —
     * it builds cleanly and then throws `AbstractMethodError` the first time the panel is shown.
     * Callers pass every destination explicitly instead.
     */
    @Composable
    fun Panel(
        projectId: String,
        onClose: () -> Unit,
        onOpenDiff: (String, GitDiffTarget) -> Unit,
        onOpenHistory: () -> Unit,
        onOpenBranches: () -> Unit,
        onOpenTags: () -> Unit,
        onOpenStashes: () -> Unit,
        onOpenConflicts: () -> Unit,
    )
}
