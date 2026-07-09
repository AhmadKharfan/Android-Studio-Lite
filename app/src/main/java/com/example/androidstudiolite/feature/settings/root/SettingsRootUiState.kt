package com.example.androidstudiolite.feature.settings.root
import androidx.compose.runtime.Immutable

@Immutable
data class SettingsRootUiState(
    val query: String = "",
    val shareUsageStats: Boolean = false,
)
