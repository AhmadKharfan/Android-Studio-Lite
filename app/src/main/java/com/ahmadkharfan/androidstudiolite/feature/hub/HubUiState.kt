package com.ahmadkharfan.androidstudiolite.feature.hub
import androidx.compose.runtime.Immutable

@Immutable
data class HubProjectUiModel(
    val id: String,
    val name: String,
    val path: String,
    val language: String,
    val lastOpenedText: String?,
)

sealed interface HubDialogUiState {
    data object None : HubDialogUiState
    @Immutable
    data class ResumeProject(val projectId: String, val projectName: String, val path: String) : HubDialogUiState
    @Immutable
    data class InvalidFolder(val path: String) : HubDialogUiState
    @Immutable
    data class RenameProject(val id: String, val currentName: String) : HubDialogUiState
    @Immutable
    data class DeleteProject(val id: String, val name: String) : HubDialogUiState
}

sealed interface HubSheetUiState {
    data object None : HubSheetUiState
    data object OpenProject : HubSheetUiState
    data object CloneRepo : HubSheetUiState
}

@Immutable
data class HubUiState(
    val greeting: String = "",
    val resumeProject: HubProjectUiModel? = null,
    val recentProjects: List<HubProjectUiModel> = emptyList(),
    val isLoadingRecents: Boolean = true,
    val dialog: HubDialogUiState = HubDialogUiState.None,
    val sheet: HubSheetUiState = HubSheetUiState.None,
    val projectMenu: HubProjectUiModel? = null,
)
