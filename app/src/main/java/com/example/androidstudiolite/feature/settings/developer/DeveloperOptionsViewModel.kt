package com.example.androidstudiolite.feature.settings.developer

import com.example.androidstudiolite.core.BaseViewModel
import com.example.androidstudiolite.domain.repository.IdeConfigRepository
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

class DeveloperOptionsViewModel(
    private val ideConfigRepository: IdeConfigRepository,
) : BaseViewModel<DeveloperOptionsUiState, DeveloperOptionsEffect>(
    initialState = DeveloperOptionsUiState(),
), DeveloperOptionsInteractionListener {

    init {
        tryToCollect(
            block = { ideConfigRepository.observeState() },
            onCollect = { ideState ->
                updateState { copy(simulateOfflineNetwork = !ideState.networkAvailable) }
            },
        )
    }

    override fun onToggleSimulateOffline(enabled: Boolean) {
        viewModelScope.launch {
            ideConfigRepository.setNetworkAvailable(!enabled)
        }
    }

    override fun onBack() {
        emitEffect(DeveloperOptionsEffect.NavigateBack)
    }

    override fun onOpenUiDesigner() {
        emitEffect(DeveloperOptionsEffect.NavigateToUiDesigner)
    }

    override fun onSimulateCrash() {
        emitEffect(DeveloperOptionsEffect.SimulateCrash)
    }

    override fun onSimulateAcsMissing() {
        emitEffect(DeveloperOptionsEffect.SimulateAcsMissing)
    }

    override fun onSimulateUnsupportedDevice() {
        emitEffect(DeveloperOptionsEffect.SimulateUnsupportedDevice)
    }

    override fun onSimulateSdCardInstall() {
        emitEffect(DeveloperOptionsEffect.SimulateSdCardInstall)
    }

    override fun onSimulateSecondaryUser() {
        emitEffect(DeveloperOptionsEffect.SimulateSecondaryUser)
    }
}
