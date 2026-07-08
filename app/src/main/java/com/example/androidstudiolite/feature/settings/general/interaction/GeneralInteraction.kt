package com.example.androidstudiolite.feature.settings.general.interaction

import com.example.androidstudiolite.domain.model.AppThemeMode

sealed interface GeneralInteraction {
    data class ThemeModeChanged(val mode: AppThemeMode) : GeneralInteraction
    data class AccentChanged(val id: String) : GeneralInteraction
    data class LanguageChanged(val language: String) : GeneralInteraction
    data class ToggleAutoOpenLastProject(val enabled: Boolean) : GeneralInteraction
    data class ToggleSnowfallEasterEgg(val enabled: Boolean) : GeneralInteraction
}
