package com.example.androidstudiolite.feature.settings.developer
import androidx.compose.runtime.Immutable

@Immutable
data class DeveloperOptionsUiState(
    val simulateOfflineNetwork: Boolean = false,
)
