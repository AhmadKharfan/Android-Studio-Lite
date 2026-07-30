package com.ahmadkharfan.androidstudiolite.feature.terminal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ahmadkharfan.androidstudiolite.feature.terminal.api.TerminalFeatureApi

internal class TerminalFeatureApiImpl : TerminalFeatureApi {

    @Composable
    override fun TerminalEntry(onBack: () -> Unit) = TerminalRoute(onBack = onBack)

    @Composable
    override fun EmbeddedEntry(projectRootPath: String, modifier: Modifier) =
        EditorEmbeddedTerminal(projectRootPath = projectRootPath, modifier = modifier)
}
