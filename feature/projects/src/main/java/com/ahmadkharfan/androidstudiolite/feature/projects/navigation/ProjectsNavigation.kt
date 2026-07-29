package com.ahmadkharfan.androidstudiolite.feature.projects.navigation

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.ahmadkharfan.androidstudiolite.feature.createproject.CreateProjectRoute
import com.ahmadkharfan.androidstudiolite.feature.folderpicker.FolderPickerRoute
import com.ahmadkharfan.androidstudiolite.feature.hub.HubRoute

/**
 * @param onOpenProject leaving for the editor is the host's decision; this feature does not know
 *   the editor's route.
 */
fun NavGraphBuilder.projectsGraph(
    navigateTo: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenPreferences: () -> Unit,
    onCreated: (String) -> Unit,
    popBackTo: (String) -> Unit,
    popBack: () -> Unit,
    setPreviousResult: (String, String) -> Unit,
) {
    composable(ProjectsRoutes.HUB) { backStackEntry ->
        val pickedFolder by backStackEntry.savedStateHandle
            .getStateFlow<String?>(ProjectsRoutes.PICKED_FOLDER_RESULT, null)
            .collectAsState()
        HubRoute(
            onOpenProject = onOpenProject,
            onCreateProject = { navigateTo(ProjectsRoutes.CREATE_PROJECT) },
            onOpenPreferences = onOpenPreferences,
            onBrowseFolder = { navigateTo(ProjectsRoutes.FOLDER_PICKER) },
            pickedFolder = pickedFolder,
            onPickedFolderConsumed = {
                backStackEntry.savedStateHandle[ProjectsRoutes.PICKED_FOLDER_RESULT] = null
            },
        )
    }

    composable(ProjectsRoutes.CREATE_PROJECT) { backStackEntry ->
        val pickedFolder by backStackEntry.savedStateHandle
            .getStateFlow<String?>(ProjectsRoutes.PICKED_FOLDER_RESULT, null)
            .collectAsState()
        CreateProjectRoute(
            onBack = { popBackTo(ProjectsRoutes.CREATE_PROJECT) },
            onCreated = onCreated,
            onBrowseLocation = { navigateTo(ProjectsRoutes.FOLDER_PICKER) },
            pickedFolder = pickedFolder,
            onPickedFolderConsumed = {
                backStackEntry.savedStateHandle[ProjectsRoutes.PICKED_FOLDER_RESULT] = null
            },
        )
    }

    composable(ProjectsRoutes.FOLDER_PICKER) {
        FolderPickerRoute(
            onCancel = popBack,
            onFolderSelected = { path ->
                setPreviousResult(ProjectsRoutes.PICKED_FOLDER_RESULT, path)
                popBack()
            },
        )
    }
}
