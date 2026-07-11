package com.example.androidstudiolite.feature.settings.general

import com.example.androidstudiolite.domain.model.AppThemeMode

interface GeneralInteractionListener {
    fun onThemeModeChanged(mode: AppThemeMode)
    fun onAccentChanged(id: String)
    fun onLanguageChanged(language: String)
    fun onToggleAutoOpenLastProject(enabled: Boolean)
    fun onToggleSnowfallEasterEgg(enabled: Boolean)
}
