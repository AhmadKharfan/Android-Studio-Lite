package com.example.androidstudiolite.feature.settings.ideconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.domain.model.IdeComponent
import com.example.androidstudiolite.domain.repository.IdeConfigRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class IdeConfigViewModel(
    private val ideConfigRepository: IdeConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IdeConfigUiState())
    val uiState: StateFlow<IdeConfigUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ideConfigRepository.observeState().collect { state ->
                _uiState.value = IdeConfigUiState(
                    components = state.components.map { it.toUiModel() },
                    offlineMode = state.offlineMode,
                    networkAvailable = state.networkAvailable,
                )
            }
        }
    }

    fun onInteraction(interaction: IdeConfigInteraction) {
        viewModelScope.launch {
            when (interaction) {
                is IdeConfigInteraction.InstallComponent -> ideConfigRepository.installComponent(interaction.id)
                is IdeConfigInteraction.ToggleOfflineMode -> ideConfigRepository.setOfflineMode(interaction.enabled)
                IdeConfigInteraction.RetryConnection -> {
                    delay(700)
                    ideConfigRepository.setNetworkAvailable(true)
                }
            }
        }
    }

    private fun IdeComponent.toUiModel() = IdeComponentUiModel(
        id = id,
        icon = icon,
        title = title,
        subtitle = subtitle,
        status = status,
    )
}
