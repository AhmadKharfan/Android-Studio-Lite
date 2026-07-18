package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.AppPreferences
import com.ahmadkharfan.androidstudiolite.domain.model.AppThemeMode
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    fun observePreferences(): Flow<AppPreferences>
    suspend fun setThemeMode(mode: AppThemeMode)
    suspend fun setEditorFontSize(size: Int)
    suspend fun setEditorTheme(id: String)
    suspend fun setEditorFontFamily(family: String)
    suspend fun setAccent(id: String)
    suspend fun setAutoOpenLastProject(enabled: Boolean)

    /** Sets the editor color scheme if the user has never chosen one explicitly. */
    suspend fun ensureEditorThemeDefault(isDarkUi: Boolean)

    /** Generic updater for simple settings fields that don't warrant a dedicated setter + use case. */
    suspend fun update(transform: (AppPreferences) -> AppPreferences)
}
