package com.example.androidstudiolite.feature.settings.developer

sealed interface DeveloperOptionsEffect {
    data object NavigateBack : DeveloperOptionsEffect
    data object NavigateToUiDesigner : DeveloperOptionsEffect
    data object SimulateCrash : DeveloperOptionsEffect
    data object SimulateAcsMissing : DeveloperOptionsEffect
    data object SimulateUnsupportedDevice : DeveloperOptionsEffect
    data object SimulateSdCardInstall : DeveloperOptionsEffect
    data object SimulateSecondaryUser : DeveloperOptionsEffect
}
