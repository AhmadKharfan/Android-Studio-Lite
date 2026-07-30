package com.ahmadkharfan.androidstudiolite.feature.projects

import androidx.compose.runtime.Composable
import com.ahmadkharfan.androidstudiolite.feature.projects.api.ProjectsFeatureApi
import com.ahmadkharfan.androidstudiolite.feature.projects.createproject.CreateProjectRoute
import com.ahmadkharfan.androidstudiolite.feature.projects.folderpicker.FolderPickerRoute
import com.ahmadkharfan.androidstudiolite.feature.projects.hub.HubRoute

internal class ProjectsFeatureApiImpl : ProjectsFeatureApi {

    @Composable
    override fun HubEntry(
        pickedFolder: String?,
        onConsumePickedFolder: () -> Unit,
        onOpenProject: (projectId: String) -> Unit,
        onCreateProject: () -> Unit,
        onBrowseFolder: () -> Unit,
        onOpenPreferences: () -> Unit,
    ) = HubRoute(
        onOpenProject = onOpenProject,
        onCreateProject = onCreateProject,
        onOpenPreferences = onOpenPreferences,
        onBrowseFolder = onBrowseFolder,
        pickedFolder = pickedFolder,
        onPickedFolderConsumed = onConsumePickedFolder,
    )

    @Composable
    override fun CreateProjectEntry(
        pickedFolder: String?,
        onConsumePickedFolder: () -> Unit,
        onBack: () -> Unit,
        onCreate: (projectId: String) -> Unit,
        onBrowseLocation: () -> Unit,
    ) = CreateProjectRoute(
        onBack = onBack,
        onCreated = onCreate,
        onBrowseLocation = onBrowseLocation,
        pickedFolder = pickedFolder,
        onPickedFolderConsumed = onConsumePickedFolder,
    )

    @Composable
    override fun FolderPickerEntry(onCancel: () -> Unit, onSelectFolder: (path: String) -> Unit) =
        FolderPickerRoute(onCancel = onCancel, onFolderSelected = onSelectFolder)
}
