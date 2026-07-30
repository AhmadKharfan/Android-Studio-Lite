package com.ahmadkharfan.androidstudiolite.feature.settings.api

import androidx.compose.runtime.Composable

/** Settings as the host sees it: one composable per destination. No default arguments. */
public interface SettingsFeatureApi {

    @Composable
    public fun RootEntry(onBack: () -> Unit, onOpen: (route: String) -> Unit)

    @Composable
    public fun GeneralEntry(onBack: () -> Unit)

    @Composable
    public fun EditorEntry(onBack: () -> Unit)

    @Composable
    public fun AiAgentEntry(onBack: () -> Unit)

    @Composable
    public fun BuildRunEntry(onBack: () -> Unit)

    @Composable
    public fun GitAuthEntry(onBack: () -> Unit)

    @Composable
    public fun AboutEntry(onBack: () -> Unit)
}
