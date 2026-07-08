package com.example.androidstudiolite.feature.settings.developer.uiState

import androidx.compose.runtime.Immutable

@Immutable
data class DeveloperOptionsUiState(
    val simulateOfflineNetwork: Boolean = false,
)
