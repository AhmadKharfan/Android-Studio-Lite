package com.example.androidstudiolite.feature.hub.uiState

data class HubProjectUiModel(
    val id: String,
    val name: String,
    val path: String,
    val language: String,
    val lastOpenedText: String?,
)

sealed interface HubDialogUiState {
    data object None : HubDialogUiState
    data class ResumeProject(val projectId: String, val projectName: String, val path: String) : HubDialogUiState
    data class UpdateAvailable(val fromVersion: String, val toVersion: String, val sizeMb: Int, val notes: String) : HubDialogUiState
    data class InvalidFolder(val path: String) : HubDialogUiState
}

data class HubUiState(
    val greeting: String = "Good morning",
    val userName: String = "Alex",
    val resumeProject: HubProjectUiModel? = null,
    val recentProjects: List<HubProjectUiModel> = emptyList(),
    val isLoadingRecents: Boolean = true,
    val dialog: HubDialogUiState = HubDialogUiState.None,
)
