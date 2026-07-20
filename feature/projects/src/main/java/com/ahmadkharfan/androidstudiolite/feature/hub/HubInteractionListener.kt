package com.ahmadkharfan.androidstudiolite.feature.hub

interface HubInteractionListener {
    fun onOpenProject(id: String)
    fun onProjectMenu(id: String)
    fun onResumeProject()
    fun onDismissResume()
    fun onCreateProject()
    fun onOpenProjectPicker()
    fun onCloneRepository()
    fun onOpenPreferences()
    fun onDismissProjectMenu()
    fun onRequestRenameProject()
    fun onRequestDeleteProject()
    fun onConfirmRenameProject(newName: String)
    fun onConfirmDeleteProject()
    fun onDismissProjectDialog()
    fun onDismissSheet()
    fun onConfirmResumeDialog()
    fun onDismissResumeDialog()
    fun onFolderPicked(path: String)
    fun onDismissInvalidFolderDialog()
    fun onConfirmInvalidFolderDialog(onReopenPicker: () -> Unit)
}
