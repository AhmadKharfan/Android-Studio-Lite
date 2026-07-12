package com.ahmadkharfan.androidstudiolite.data.fake

import com.ahmadkharfan.androidstudiolite.domain.model.AppPreferences
import com.ahmadkharfan.androidstudiolite.domain.model.AppThemeMode
import com.ahmadkharfan.androidstudiolite.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakePreferencesRepository : PreferencesRepository {

    private val preferences = MutableStateFlow(AppPreferences())

    override fun observePreferences(): StateFlow<AppPreferences> = preferences

    override suspend fun setThemeMode(mode: AppThemeMode) {
        preferences.value = preferences.value.copy(themeMode = mode)
    }

    override suspend fun setEditorFontSize(size: Int) {
        preferences.value = preferences.value.copy(editorFontSize = size)
    }

    override suspend fun setEditorTheme(id: String) {
        preferences.value = preferences.value.copy(editorThemeId = id)
    }

    override suspend fun setShareUsageStats(enabled: Boolean) {
        preferences.value = preferences.value.copy(shareUsageStats = enabled)
    }

    override suspend fun setAccent(id: String) {
        preferences.value = preferences.value.copy(accentId = id)
    }

    override suspend fun setLanguage(language: String) {
        preferences.value = preferences.value.copy(language = language)
    }

    override suspend fun setAutoOpenLastProject(enabled: Boolean) {
        preferences.value = preferences.value.copy(autoOpenLastProject = enabled)
    }

    override suspend fun setSnowfallEasterEgg(enabled: Boolean) {
        preferences.value = preferences.value.copy(snowfallEasterEgg = enabled)
    }

    override suspend fun update(transform: (AppPreferences) -> AppPreferences) {
        preferences.value = transform(preferences.value)
    }
}
