package com.ahmadkharfan.androidstudiolite.feature.settings.editor
import androidx.compose.runtime.Immutable

@Immutable
data class EditorSettingsUiState(
    val fontSize: Int = 13,
    val colorSchemeId: String = "darcula",
    val tabSize: Int = 4,
    val autoSave: Boolean = true,
    val kotlinLsp: Boolean = true,
    val javaLsp: Boolean = true,
    val xmlLsp: Boolean = false,
)
