package com.ahmadkharfan.androidstudiolite.feature.projects.api

import androidx.compose.runtime.Composable

/**
 * The project hub and its wizards as the host sees it. Opening a project is the host's decision, so
 * [HubEntry] reports the id rather than navigating. No default arguments.
 */
public interface ProjectsFeatureApi {

    @Composable
    public fun HubEntry(
        pickedFolder: String?,
        onConsumePickedFolder: () -> Unit,
        onOpenProject: (projectId: String) -> Unit,
        onCreateProject: () -> Unit,
        onBrowseFolder: () -> Unit,
        onOpenPreferences: () -> Unit,
    )

    @Composable
    public fun CreateProjectEntry(
        pickedFolder: String?,
        onConsumePickedFolder: () -> Unit,
        onBack: () -> Unit,
        onCreate: (projectId: String) -> Unit,
        onBrowseLocation: () -> Unit,
    )

    @Composable
    public fun FolderPickerEntry(onCancel: () -> Unit, onSelectFolder: (path: String) -> Unit)
}
