package com.example.androidstudiolite.feature.hub.interaction

sealed interface HubInteraction {
    data class OpenProject(val id: String) : HubInteraction
    data class ProjectMenu(val id: String) : HubInteraction
    data object ResumeProject : HubInteraction
    data object DismissResume : HubInteraction
    data object CreateProject : HubInteraction
    data object OpenProjectPicker : HubInteraction
    data object CloneRepository : HubInteraction
    data object OpenPreferences : HubInteraction
    data object OpenTerminal : HubInteraction
    data object OpenIdeConfig : HubInteraction
    data object OpenDocs : HubInteraction
}
