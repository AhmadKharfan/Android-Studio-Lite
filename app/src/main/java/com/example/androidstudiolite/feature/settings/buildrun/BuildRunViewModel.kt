package com.example.androidstudiolite.feature.settings.buildrun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BuildRunViewModel(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuildRunUiState())
    val uiState: StateFlow<BuildRunUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.observePreferences().collect { prefs ->
                _uiState.value = BuildRunUiState(
                    gradleJvmPath = prefs.gradleJvmPath,
                    parallelTaskExecution = prefs.parallelTaskExecution,
                    buildCacheEnabled = prefs.buildCacheEnabled,
                    configurationCacheEnabled = prefs.configurationCacheEnabled,
                    launchAfterInstall = prefs.launchAfterInstall,
                    installViaShizuku = prefs.installViaShizuku,
                )
            }
        }
    }

    fun onInteraction(interaction: BuildRunInteraction) {
        viewModelScope.launch {
            when (interaction) {
                is BuildRunInteraction.GradleJvmPathChanged -> preferencesRepository.update { it.copy(gradleJvmPath = interaction.path) }
                is BuildRunInteraction.ToggleParallelTaskExecution -> preferencesRepository.update { it.copy(parallelTaskExecution = interaction.enabled) }
                is BuildRunInteraction.ToggleBuildCache -> preferencesRepository.update { it.copy(buildCacheEnabled = interaction.enabled) }
                is BuildRunInteraction.ToggleConfigurationCache -> preferencesRepository.update { it.copy(configurationCacheEnabled = interaction.enabled) }
                is BuildRunInteraction.ToggleLaunchAfterInstall -> preferencesRepository.update { it.copy(launchAfterInstall = interaction.enabled) }
                is BuildRunInteraction.ToggleInstallViaShizuku -> preferencesRepository.update { it.copy(installViaShizuku = interaction.enabled) }
            }
        }
    }
}
