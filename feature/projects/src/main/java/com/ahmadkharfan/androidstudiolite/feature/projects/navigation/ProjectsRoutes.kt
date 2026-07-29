package com.ahmadkharfan.androidstudiolite.feature.projects.navigation

/** Routes owned by the projects feature. */
object ProjectsRoutes {
    const val HUB = "hub"
    const val CREATE_PROJECT = "createProject"
    const val FOLDER_PICKER = "folderPicker"

    /**
     * Saved-state key for handing a browsed folder back to whichever screen requested it. Both the
     * producer and the consumers live in this feature, so the protocol stays here.
     */
    const val PICKED_FOLDER_RESULT = "picked_folder"
}
