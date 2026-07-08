package com.example.androidstudiolite.feature.onboarding.statistics.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.di.AppContainer
import com.example.androidstudiolite.domain.usecase.ObservePreferencesUseCase
import com.example.androidstudiolite.domain.usecase.UpdateShareUsageStatsUseCase
import com.example.androidstudiolite.feature.onboarding.statistics.interaction.StatisticsInteraction
import com.example.androidstudiolite.feature.onboarding.statistics.uiState.StatisticsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StatisticsViewModel(
    private val observePreferences: ObservePreferencesUseCase = ObservePreferencesUseCase(AppContainer.preferencesRepository),
    private val updateShareUsageStats: UpdateShareUsageStatsUseCase = UpdateShareUsageStatsUseCase(AppContainer.preferencesRepository),
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observePreferences().collect { prefs ->
                _uiState.value = StatisticsUiState(shareUsageStats = prefs.shareUsageStats)
            }
        }
    }

    fun onInteraction(interaction: StatisticsInteraction) {
        when (interaction) {
            is StatisticsInteraction.ToggleShareUsageStats -> {
                _uiState.value = _uiState.value.copy(shareUsageStats = interaction.enabled)
                viewModelScope.launch { updateShareUsageStats(interaction.enabled) }
            }
        }
    }
}
