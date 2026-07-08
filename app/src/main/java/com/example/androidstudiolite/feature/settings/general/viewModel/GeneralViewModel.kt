package com.example.androidstudiolite.feature.settings.general.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.di.AppContainer
import com.example.androidstudiolite.domain.usecase.ObservePreferencesUseCase
import com.example.androidstudiolite.domain.usecase.UpdateAccentUseCase
import com.example.androidstudiolite.domain.usecase.UpdateAutoOpenLastProjectUseCase
import com.example.androidstudiolite.domain.usecase.UpdateLanguageUseCase
import com.example.androidstudiolite.domain.usecase.UpdateSnowfallEasterEggUseCase
import com.example.androidstudiolite.domain.usecase.UpdateThemeModeUseCase
import com.example.androidstudiolite.feature.settings.general.interaction.GeneralInteraction
import com.example.androidstudiolite.feature.settings.general.uiState.GeneralUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GeneralViewModel(
    private val observePreferences: ObservePreferencesUseCase = ObservePreferencesUseCase(AppContainer.preferencesRepository),
    private val updateThemeMode: UpdateThemeModeUseCase = UpdateThemeModeUseCase(AppContainer.preferencesRepository),
    private val updateAccent: UpdateAccentUseCase = UpdateAccentUseCase(AppContainer.preferencesRepository),
    private val updateLanguage: UpdateLanguageUseCase = UpdateLanguageUseCase(AppContainer.preferencesRepository),
    private val updateAutoOpenLastProject: UpdateAutoOpenLastProjectUseCase = UpdateAutoOpenLastProjectUseCase(AppContainer.preferencesRepository),
    private val updateSnowfallEasterEgg: UpdateSnowfallEasterEggUseCase = UpdateSnowfallEasterEggUseCase(AppContainer.preferencesRepository),
) : ViewModel() {

    private val _uiState = MutableStateFlow(GeneralUiState())
    val uiState: StateFlow<GeneralUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observePreferences().collect { prefs ->
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
                is GeneralInteraction.ThemeModeChanged -> updateThemeMode(interaction.mode)
                is GeneralInteraction.AccentChanged -> updateAccent(interaction.id)
                is GeneralInteraction.LanguageChanged -> updateLanguage(interaction.language)
                is GeneralInteraction.ToggleAutoOpenLastProject -> updateAutoOpenLastProject(interaction.enabled)
                is GeneralInteraction.ToggleSnowfallEasterEgg -> updateSnowfallEasterEgg(interaction.enabled)
            }
        }
    }
}
