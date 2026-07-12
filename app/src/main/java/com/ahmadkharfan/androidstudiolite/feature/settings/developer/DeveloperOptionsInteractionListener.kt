package com.ahmadkharfan.androidstudiolite.feature.settings.developer

interface DeveloperOptionsInteractionListener {
    fun onBack()
    fun onOpenUiDesigner()
    fun onSimulateCrash()
    fun onSimulateAcsMissing()
    fun onSimulateUnsupportedDevice()
    fun onSimulateSdCardInstall()
    fun onSimulateSecondaryUser()
}
