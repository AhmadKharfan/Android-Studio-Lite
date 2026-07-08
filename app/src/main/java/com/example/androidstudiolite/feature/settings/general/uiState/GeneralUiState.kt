package com.example.androidstudiolite.feature.settings.general.uiState

import com.example.androidstudiolite.domain.model.AppThemeMode

data class GeneralUiState(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val accentId: String = "emerald",
    val language: String = "en",
    val autoOpenLastProject: Boolean = true,
    val snowfallEasterEgg: Boolean = false,
)
