package com.example.androidstudiolite.feature.settings.ideconfig
import androidx.compose.runtime.Immutable

enum class IdeConfigComponentStatus { NotInstalled, Downloading, Verifying, Extracting, Installed, Failed }

@Immutable
data class IdeComponentUiModel(
    val id: String,
    val icon: String,
    val title: String,
    val subtitle: String,
    val status: IdeConfigComponentStatus,
    val progressPercent: Int = 0,
    val errorMessage: String? = null,
)

@Immutable
data class IdeConfigUiState(
    val components: List<IdeComponentUiModel> = emptyList(),
    val isInstalling: Boolean = false,
    val unsupportedDevice: Boolean = false,
    val networkAvailable: Boolean = true,
)
