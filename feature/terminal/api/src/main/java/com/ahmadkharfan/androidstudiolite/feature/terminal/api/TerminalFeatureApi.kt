package com.ahmadkharfan.androidstudiolite.feature.terminal.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The terminal as the host sees it: a full screen, and an embeddable panel the editor can host in
 * its bottom bar without depending on this feature's implementation.
 *
 * No default argument values on these members; see [TerminalRoutes] siblings for why.
 */
public interface TerminalFeatureApi {

    @Composable
    public fun TerminalEntry(onBack: () -> Unit)

    /** The panel form, sized by the caller. */
    @Composable
    public fun EmbeddedEntry(projectRootPath: String, modifier: Modifier)
}
