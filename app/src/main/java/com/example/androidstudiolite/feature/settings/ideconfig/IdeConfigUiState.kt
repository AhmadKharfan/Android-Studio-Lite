package com.example.androidstudiolite.feature.settings.ideconfig
import androidx.compose.runtime.Immutable

import com.example.androidstudiolite.domain.model.IdeComponentStatus

@Immutable
data class IdeComponentUiModel(
    val id: String,
    val icon: String,
    val title: String,
    val subtitle: String,
    val status: IdeComponentStatus,
)

@Immutable
data class IdeConfigUiState(
    val components: List<IdeComponentUiModel> = emptyList(),
    val offlineMode: Boolean = false,
    val networkAvailable: Boolean = true,
)
