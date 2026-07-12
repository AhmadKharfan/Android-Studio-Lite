package com.ahmadkharfan.androidstudiolite.feature.settings.general
import androidx.compose.runtime.Immutable

import com.ahmadkharfan.androidstudiolite.domain.model.AppThemeMode

@Immutable
data class GeneralUiState(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val accentId: String = "emerald",
    val language: String = "en",
    val autoOpenLastProject: Boolean = true,
    val snowfallEasterEgg: Boolean = false,
)
