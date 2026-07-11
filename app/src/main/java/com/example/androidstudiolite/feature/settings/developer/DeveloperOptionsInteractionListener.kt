package com.example.androidstudiolite.feature.settings.developer

interface DeveloperOptionsInteractionListener {
    fun onToggleSimulateOffline(enabled: Boolean)
    fun onBack()
    fun onOpenUiDesigner()
    fun onSimulateCrash()
    fun onSimulateAcsMissing()
    fun onSimulateUnsupportedDevice()
    fun onSimulateSdCardInstall()
    fun onSimulateSecondaryUser()
}
