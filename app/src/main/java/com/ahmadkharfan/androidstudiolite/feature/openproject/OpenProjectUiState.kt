package com.ahmadkharfan.androidstudiolite.feature.openproject
import androidx.compose.runtime.Immutable

@Immutable
data class OpenProjectItemUiModel(
    val id: String,
    val name: String,
    val subtitle: String,
)

@Immutable
data class OpenProjectUiState(
    val query: String = "",
    val allProjects: List<OpenProjectItemUiModel> = emptyList(),
    val filteredProjects: List<OpenProjectItemUiModel> = emptyList(),
)
