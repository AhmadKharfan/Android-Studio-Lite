package com.example.androidstudiolite.feature.settings.developer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.domain.repository.IdeConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeveloperOptionsViewModel(
    private val ideConfigRepository: IdeConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeveloperOptionsUiState())
    val uiState: StateFlow<DeveloperOptionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ideConfigRepository.observeState().collect { state ->
                _uiState.value = _uiState.value.copy(simulateOfflineNetwork = !state.networkAvailable)
            }
        }
    }

    fun onInteraction(interaction: DeveloperOptionsInteraction) {
        viewModelScope.launch {
            when (interaction) {
                is DeveloperOptionsInteraction.ToggleSimulateOffline -> ideConfigRepository.setNetworkAvailable(!interaction.enabled)
            }
        }
    }
}
