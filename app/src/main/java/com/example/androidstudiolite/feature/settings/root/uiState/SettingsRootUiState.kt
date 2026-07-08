package com.example.androidstudiolite.feature.settings.root.uiState

import androidx.compose.runtime.Immutable

@Immutable
data class SettingsRootUiState(
    val query: String = "",
    val shareUsageStats: Boolean = false,
)
