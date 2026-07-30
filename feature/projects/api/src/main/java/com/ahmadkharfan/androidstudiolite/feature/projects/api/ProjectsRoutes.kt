package com.ahmadkharfan.androidstudiolite.feature.projects.api

/** Routes owned by the projects feature. */
public object ProjectsRoutes {
    public const val HUB: String = "hub"
    public const val CREATE_PROJECT: String = "createProject"
    public const val FOLDER_PICKER: String = "folderPicker"

    /**
     * Saved-state key for handing a browsed folder back to whichever screen requested it. Both the
     * producer and the consumers live in this feature, so the protocol stays here.
     */
    public const val PICKED_FOLDER_RESULT: String = "picked_folder"
}
