package com.example.androidstudiolite.feature.editor.interaction

import com.example.androidstudiolite.feature.editor.uiState.EditorRailTool

sealed interface EditorInteraction {
    data class SelectTab(val id: String) : EditorInteraction
    data class CloseTab(val id: String) : EditorInteraction
    data object ToggleMenu : EditorInteraction
    data class SelectRailTool(val tool: EditorRailTool) : EditorInteraction
    data object CloseDrawer : EditorInteraction
    data class ToggleFolder(val id: String) : EditorInteraction
    data class OpenFile(val id: String, val name: String) : EditorInteraction
    data object RunProject : EditorInteraction
    data class SelectBottomTab(val id: String) : EditorInteraction
    data object ToggleBottomPanel : EditorInteraction
    data object CloseProject : EditorInteraction
    data object OpenSettings : EditorInteraction
    data object OpenAiAgentSettings : EditorInteraction
    data object Save : EditorInteraction
    data object SnackbarShown : EditorInteraction
    data object SimulateBuildFailure : EditorInteraction
    data object ToggleMemoryPressure : EditorInteraction
    data object ToggleMemoryChartExpanded : EditorInteraction
    data object FreeUpMemory : EditorInteraction
    data object SimulateLspReindex : EditorInteraction
    data object ToggleFindBar : EditorInteraction
    data class FindQueryChanged(val query: String) : EditorInteraction
    data object FindNext : EditorInteraction
    data object FindPrevious : EditorInteraction
    data object ToggleAutocompleteDemo : EditorInteraction
}
