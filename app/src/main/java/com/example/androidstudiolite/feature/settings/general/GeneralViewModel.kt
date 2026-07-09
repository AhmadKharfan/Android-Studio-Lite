package com.example.androidstudiolite.feature.settings.general

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GeneralViewModel(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GeneralUiState())
    val uiState: StateFlow<GeneralUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.observePreferences().collect { prefs ->
                _uiState.value = GeneralUiState(
                    themeMode = prefs.themeMode,
                    accentId = prefs.accentId,
                    language = prefs.language,
                    autoOpenLastProject = prefs.autoOpenLastProject,
                    snowfallEasterEgg = prefs.snowfallEasterEgg,
                )
            }
        }
    }

    fun onInteraction(interaction: GeneralInteraction) {
        viewModelScope.launch {
            when (interaction) {
                is GeneralInteraction.ThemeModeChanged -> preferencesRepository.setThemeMode(interaction.mode)
                is GeneralInteraction.AccentChanged -> preferencesRepository.setAccent(interaction.id)
                is GeneralInteraction.LanguageChanged -> preferencesRepository.setLanguage(interaction.language)
                is GeneralInteraction.ToggleAutoOpenLastProject -> preferencesRepository.setAutoOpenLastProject(interaction.enabled)
                is GeneralInteraction.ToggleSnowfallEasterEgg -> preferencesRepository.setSnowfallEasterEgg(interaction.enabled)
            }
        }
    }
}
