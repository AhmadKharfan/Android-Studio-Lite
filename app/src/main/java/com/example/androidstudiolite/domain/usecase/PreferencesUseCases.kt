package com.example.androidstudiolite.domain.usecase

import com.example.androidstudiolite.domain.model.AppPreferences
import com.example.androidstudiolite.domain.model.AppThemeMode
import com.example.androidstudiolite.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow

class ObservePreferencesUseCase(private val repository: PreferencesRepository) {
    operator fun invoke(): Flow<AppPreferences> = repository.observePreferences()
}

class UpdateThemeModeUseCase(private val repository: PreferencesRepository) {
    suspend operator fun invoke(mode: AppThemeMode) = repository.setThemeMode(mode)
}

class UpdateEditorFontSizeUseCase(private val repository: PreferencesRepository) {
    suspend operator fun invoke(size: Int) = repository.setEditorFontSize(size)
}

class UpdateEditorThemeUseCase(private val repository: PreferencesRepository) {
    suspend operator fun invoke(id: String) = repository.setEditorTheme(id)
}

class UpdateShareUsageStatsUseCase(private val repository: PreferencesRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setShareUsageStats(enabled)
}

class UpdateAccentUseCase(private val repository: PreferencesRepository) {
    suspend operator fun invoke(id: String) = repository.setAccent(id)
}

class UpdateLanguageUseCase(private val repository: PreferencesRepository) {
    suspend operator fun invoke(language: String) = repository.setLanguage(language)
}

class UpdateAutoOpenLastProjectUseCase(private val repository: PreferencesRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setAutoOpenLastProject(enabled)
}

class UpdateSnowfallEasterEggUseCase(private val repository: PreferencesRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setSnowfallEasterEgg(enabled)
}

/** Backs the many simple Editor/Build & Run toggle-style settings that don't warrant their own use case. */
class UpdatePreferencesUseCase(private val repository: PreferencesRepository) {
    suspend operator fun invoke(transform: (AppPreferences) -> AppPreferences) = repository.update(transform)
}
