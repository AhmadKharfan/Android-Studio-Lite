package com.example.androidstudiolite.feature.clonerepo.uiState

import androidx.compose.runtime.Immutable

@Immutable
data class CloneOptionUiModel(val id: String, val label: String, val selected: Boolean)

@Immutable
data class CloneRepoUiState(
    val url: String = "",
    val branch: String = "",
    val options: List<CloneOptionUiModel> = listOf(
        CloneOptionUiModel("depth1", "--depth 1", selected = true),
        CloneOptionUiModel("recursive", "--recursive", selected = false),
        CloneOptionUiModel("singleBranch", "--single-branch", selected = false),
    ),
    val cloning: Boolean = false,
    val progressPercent: Int = 0,
    val progressMessage: String = "",
    val clonedProjectId: String? = null,
)
