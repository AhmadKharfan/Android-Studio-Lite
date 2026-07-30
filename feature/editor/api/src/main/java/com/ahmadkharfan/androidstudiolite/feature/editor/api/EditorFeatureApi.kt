package com.ahmadkharfan.androidstudiolite.feature.editor.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The editor workbench as the host sees it.
 *
 * [terminalContent] is a slot rather than a dependency: the editor describes where a terminal goes
 * and the host decides which implementation fills it, so the editor never depends on the terminal
 * feature. No default arguments — a `@Composable` interface member with defaults produces a defaults
 * bridge that does not match an implementation compiled in another module.
 */
public interface EditorFeatureApi {

    @Composable
    public fun EditorEntry(
        projectId: String,
        navigation: EditorNavigation,
        openConflictPath: String?,
        onConsumeConflictPath: () -> Unit,
        terminalContent: @Composable (projectRootPath: String, modifier: Modifier) -> Unit,
    )
}
