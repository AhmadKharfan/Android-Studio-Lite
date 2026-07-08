package com.example.androidstudiolite.feature.settings.ideconfig.uiState

import com.example.androidstudiolite.domain.model.IdeComponentStatus

data class IdeComponentUiModel(
    val id: String,
    val icon: String,
    val title: String,
    val subtitle: String,
    val status: IdeComponentStatus,
)

data class IdeConfigUiState(
    val components: List<IdeComponentUiModel> = emptyList(),
    val offlineMode: Boolean = false,
    val networkAvailable: Boolean = true,
)
