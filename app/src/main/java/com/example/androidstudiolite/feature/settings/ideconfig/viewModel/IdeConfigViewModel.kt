package com.example.androidstudiolite.feature.settings.ideconfig.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.domain.model.IdeComponent
import com.example.androidstudiolite.domain.usecase.InstallIdeComponentUseCase
import com.example.androidstudiolite.domain.usecase.ObserveIdeConfigStateUseCase
import com.example.androidstudiolite.domain.usecase.SetNetworkAvailableUseCase
import com.example.androidstudiolite.domain.usecase.SetOfflineModeUseCase
import com.example.androidstudiolite.feature.settings.ideconfig.interaction.IdeConfigInteraction
import com.example.androidstudiolite.feature.settings.ideconfig.uiState.IdeComponentUiModel
import com.example.androidstudiolite.feature.settings.ideconfig.uiState.IdeConfigUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class IdeConfigViewModel(
    private val observeState: ObserveIdeConfigStateUseCase,
    private val installComponent: InstallIdeComponentUseCase,
    private val setOfflineMode: SetOfflineModeUseCase,
    private val setNetworkAvailable: SetNetworkAvailableUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IdeConfigUiState())
    val uiState: StateFlow<IdeConfigUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeState().collect { state ->
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
                is IdeConfigInteraction.InstallComponent -> installComponent(interaction.id)
                is IdeConfigInteraction.ToggleOfflineMode -> setOfflineMode(interaction.enabled)
                IdeConfigInteraction.RetryConnection -> {
                    delay(700)
                    setNetworkAvailable(true)
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
