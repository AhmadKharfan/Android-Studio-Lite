package com.example.androidstudiolite.feature.hub

sealed interface HubEffect {
    data class NavigateToProject(val id: String) : HubEffect
    data object NavigateToCreateProject : HubEffect
    data object NavigateToPreferences : HubEffect
    data object NavigateToTerminal : HubEffect
    data object NavigateToIdeConfig : HubEffect
    data object NavigateToDocs : HubEffect
}
