package com.ahmadkharfan.androidstudiolite.feature.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ahmadkharfan.androidstudiolite.feature.editor.api.EditorFeatureApi
import com.ahmadkharfan.androidstudiolite.feature.editor.api.EditorNavigation

internal class EditorFeatureApiImpl : EditorFeatureApi {

    @Composable
    override fun EditorEntry(
        projectId: String,
        navigation: EditorNavigation,
        openConflictPath: String?,
        onConsumeConflictPath: () -> Unit,
        terminalContent: @Composable (projectRootPath: String, modifier: Modifier) -> Unit,
    ) = EditorRoute(
        projectId = projectId,
        navigation = navigation,
        openConflictPath = openConflictPath,
        onConflictPathOpened = onConsumeConflictPath,
        terminalContent = terminalContent,
    )
}
