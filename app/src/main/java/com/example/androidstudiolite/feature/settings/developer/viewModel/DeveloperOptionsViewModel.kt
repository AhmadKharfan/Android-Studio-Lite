package com.example.androidstudiolite.feature.settings.developer.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.di.AppContainer
import com.example.androidstudiolite.domain.usecase.ObserveIdeConfigStateUseCase
import com.example.androidstudiolite.domain.usecase.SetNetworkAvailableUseCase
import com.example.androidstudiolite.feature.settings.developer.interaction.DeveloperOptionsInteraction
import com.example.androidstudiolite.feature.settings.developer.uiState.DeveloperOptionsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeveloperOptionsViewModel(
    private val observeIdeConfigState: ObserveIdeConfigStateUseCase = ObserveIdeConfigStateUseCase(AppContainer.ideConfigRepository),
    private val setNetworkAvailable: SetNetworkAvailableUseCase = SetNetworkAvailableUseCase(AppContainer.ideConfigRepository),
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeveloperOptionsUiState())
    val uiState: StateFlow<DeveloperOptionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeIdeConfigState().collect { state ->
                _uiState.value = _uiState.value.copy(simulateOfflineNetwork = !state.networkAvailable)
            }
        }
    }

    fun onInteraction(interaction: DeveloperOptionsInteraction) {
        viewModelScope.launch {
            when (interaction) {
                is DeveloperOptionsInteraction.ToggleSimulateOffline -> setNetworkAvailable(!interaction.enabled)
            }
        }
    }
}
