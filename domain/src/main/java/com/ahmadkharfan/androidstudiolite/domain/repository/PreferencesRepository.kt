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

    suspend fun ensureEditorThemeDefault(isDarkUi: Boolean)

    suspend fun update(transform: (AppPreferences) -> AppPreferences)

    suspend fun setSelectedVariant(projectId: String, variant: String) {}

    suspend fun getSelectedVariant(projectId: String): String? = null
}
