package com.example.androidstudiolite.feature.settings.general

import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.core.BaseViewModel
import com.example.androidstudiolite.domain.model.AppThemeMode
import com.example.androidstudiolite.domain.repository.PreferencesRepository
import kotlinx.coroutines.launch

class GeneralViewModel(
    private val preferencesRepository: PreferencesRepository,
) : BaseViewModel<GeneralUiState, Nothing>(
    initialState = GeneralUiState(),
), GeneralInteractionListener {

    init {
        tryToCollect(
            block = { preferencesRepository.observePreferences() },
            onCollect = { prefs ->
                updateState {
                    copy(
                        themeMode = prefs.themeMode,
                        accentId = prefs.accentId,
                        language = prefs.language,
                        autoOpenLastProject = prefs.autoOpenLastProject,
                        snowfallEasterEgg = prefs.snowfallEasterEgg,
                    )
                }
            },
        )
    }

    override fun onThemeModeChanged(mode: AppThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    override fun onAccentChanged(id: String) {
        viewModelScope.launch { preferencesRepository.setAccent(id) }
    }

    override fun onLanguageChanged(language: String) {
        viewModelScope.launch { preferencesRepository.setLanguage(language) }
    }

    override fun onToggleAutoOpenLastProject(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoOpenLastProject(enabled) }
    }

    override fun onToggleSnowfallEasterEgg(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSnowfallEasterEgg(enabled) }
    }
}
