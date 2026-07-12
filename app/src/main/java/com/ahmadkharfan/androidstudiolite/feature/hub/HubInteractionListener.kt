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
    fun onOpenTerminal()
    fun onOpenIdeConfig()
    fun onOpenDocs()
}
