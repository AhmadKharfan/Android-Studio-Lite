package com.example.androidstudiolite.feature.settings.root.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.domain.usecase.ObservePreferencesUseCase
import com.example.androidstudiolite.feature.settings.root.interaction.SettingsRootInteraction
import com.example.androidstudiolite.feature.settings.root.uiState.SettingsRootUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsRootViewModel(
    private val observePreferences: ObservePreferencesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsRootUiState())
    val uiState: StateFlow<SettingsRootUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observePreferences().collect { prefs ->
                _uiState.value = _uiState.value.copy(shareUsageStats = prefs.shareUsageStats)
            }
        }
    }

    fun onInteraction(interaction: SettingsRootInteraction) {
        when (interaction) {
            is SettingsRootInteraction.QueryChanged -> _uiState.value = _uiState.value.copy(query = interaction.query)
        }
    }
}
