package com.example.androidstudiolite.feature.settings.developer

import com.example.androidstudiolite.core.BaseViewModel

class DeveloperOptionsViewModel :
    BaseViewModel<DeveloperOptionsUiState, DeveloperOptionsEffect>(
        initialState = DeveloperOptionsUiState(),
    ),
    DeveloperOptionsInteractionListener {

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
