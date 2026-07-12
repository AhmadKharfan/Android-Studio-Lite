package com.ahmadkharfan.androidstudiolite.feature.settings.buildrun

import androidx.lifecycle.viewModelScope
import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.domain.repository.PreferencesRepository
import kotlinx.coroutines.launch

class BuildRunViewModel(
    private val preferencesRepository: PreferencesRepository,
) : BaseViewModel<BuildRunUiState, Nothing>(initialState = BuildRunUiState()), BuildRunInteractionListener {

    init {
        tryToCollect(
            block = { preferencesRepository.observePreferences() },
            onCollect = { prefs ->
                updateState {
                    copy(
                        gradleJvmPath = prefs.gradleJvmPath,
                        parallelTaskExecution = prefs.parallelTaskExecution,
                        buildCacheEnabled = prefs.buildCacheEnabled,
                        configurationCacheEnabled = prefs.configurationCacheEnabled,
                        launchAfterInstall = prefs.launchAfterInstall,
                        installViaShizuku = prefs.installViaShizuku,
                    )
                }
            },
        )
    }

    override fun onGradleJvmPathChanged(path: String) {
        viewModelScope.launch { preferencesRepository.update { it.copy(gradleJvmPath = path) } }
    }

    override fun onToggleParallelTaskExecution(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.update { it.copy(parallelTaskExecution = enabled) } }
    }

    override fun onToggleBuildCache(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.update { it.copy(buildCacheEnabled = enabled) } }
    }

    override fun onToggleConfigurationCache(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.update { it.copy(configurationCacheEnabled = enabled) } }
    }

    override fun onToggleLaunchAfterInstall(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.update { it.copy(launchAfterInstall = enabled) } }
    }

    override fun onToggleInstallViaShizuku(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.update { it.copy(installViaShizuku = enabled) } }
    }
}
