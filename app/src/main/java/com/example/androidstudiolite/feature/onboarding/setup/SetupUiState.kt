package com.example.androidstudiolite.feature.onboarding.setup
import androidx.compose.runtime.Immutable

enum class SetupComponentStatus { NotInstalled, Downloading, Verifying, Extracting, Installed, Failed }

@Immutable
data class SetupComponentUiModel(
    val id: String,
    val icon: String,
    val displayName: String,
    val version: String,
    val status: SetupComponentStatus,
    val progressPercent: Int,
    val detail: String,
    val errorMessage: String? = null,
)

@Immutable
data class SetupUiState(
    val components: List<SetupComponentUiModel> = emptyList(),
    val isInstalling: Boolean = false,
    val allInstalled: Boolean = false,
    val unsupportedDevice: Boolean = false,
    val setupComplete: Boolean = false,
)
