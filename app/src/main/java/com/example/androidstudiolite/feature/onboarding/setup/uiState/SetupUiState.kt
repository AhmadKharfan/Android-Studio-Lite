package com.example.androidstudiolite.feature.onboarding.setup.uiState

enum class InstallStatus { Pending, Installing, Installed, Failed }

data class SetupUiState(
    val jdkStatus: InstallStatus = InstallStatus.Installed,
    val sdkStatus: InstallStatus = InstallStatus.Pending,
    val sdkProgressPercent: Int = 0,
    val sdkDetail: String = "",
    val setupComplete: Boolean = false,
)
