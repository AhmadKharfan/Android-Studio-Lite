package com.example.androidstudiolite.feature.settings.buildrun.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.domain.usecase.ObservePreferencesUseCase
import com.example.androidstudiolite.domain.usecase.UpdatePreferencesUseCase
import com.example.androidstudiolite.feature.settings.buildrun.interaction.BuildRunInteraction
import com.example.androidstudiolite.feature.settings.buildrun.uiState.BuildRunUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BuildRunViewModel(
    private val observePreferences: ObservePreferencesUseCase,
    private val updatePreferences: UpdatePreferencesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuildRunUiState())
    val uiState: StateFlow<BuildRunUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observePreferences().collect { prefs ->
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
                is BuildRunInteraction.GradleJvmPathChanged -> updatePreferences { it.copy(gradleJvmPath = interaction.path) }
                is BuildRunInteraction.ToggleParallelTaskExecution -> updatePreferences { it.copy(parallelTaskExecution = interaction.enabled) }
                is BuildRunInteraction.ToggleBuildCache -> updatePreferences { it.copy(buildCacheEnabled = interaction.enabled) }
                is BuildRunInteraction.ToggleConfigurationCache -> updatePreferences { it.copy(configurationCacheEnabled = interaction.enabled) }
                is BuildRunInteraction.ToggleLaunchAfterInstall -> updatePreferences { it.copy(launchAfterInstall = interaction.enabled) }
                is BuildRunInteraction.ToggleInstallViaShizuku -> updatePreferences { it.copy(installViaShizuku = interaction.enabled) }
            }
        }
    }
}
