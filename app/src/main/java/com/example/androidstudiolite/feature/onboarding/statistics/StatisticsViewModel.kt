package com.example.androidstudiolite.feature.onboarding.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StatisticsViewModel(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.observePreferences().collect { prefs ->
                _uiState.value = StatisticsUiState(shareUsageStats = prefs.shareUsageStats)
            }
        }
    }

    fun onInteraction(interaction: StatisticsInteraction) {
        when (interaction) {
            is StatisticsInteraction.ToggleShareUsageStats -> {
                _uiState.value = _uiState.value.copy(shareUsageStats = interaction.enabled)
                viewModelScope.launch { preferencesRepository.setShareUsageStats(interaction.enabled) }
            }
        }
    }
}
