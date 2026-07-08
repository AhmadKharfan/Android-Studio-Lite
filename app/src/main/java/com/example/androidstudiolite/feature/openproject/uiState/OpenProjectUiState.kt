package com.example.androidstudiolite.feature.openproject.uiState

data class OpenProjectItemUiModel(
    val id: String,
    val name: String,
    val subtitle: String,
)

data class OpenProjectUiState(
    val query: String = "",
    val allProjects: List<OpenProjectItemUiModel> = emptyList(),
    val filteredProjects: List<OpenProjectItemUiModel> = emptyList(),
)
