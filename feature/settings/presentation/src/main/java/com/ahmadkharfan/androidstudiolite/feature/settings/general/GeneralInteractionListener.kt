package com.ahmadkharfan.androidstudiolite.feature.settings.general

import com.ahmadkharfan.androidstudiolite.domain.model.AppThemeMode

interface GeneralInteractionListener {
    fun onThemeModeChanged(mode: AppThemeMode)
    fun onAccentChanged(id: String)
    fun onToggleAutoOpenLastProject(enabled: Boolean)
}
