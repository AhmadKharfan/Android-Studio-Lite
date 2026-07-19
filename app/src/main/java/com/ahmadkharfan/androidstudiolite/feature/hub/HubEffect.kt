package com.ahmadkharfan.androidstudiolite.feature.hub

sealed interface HubEffect {
    data class NavigateToProject(val id: String) : HubEffect
    data object NavigateToCreateProject : HubEffect
    data object NavigateToPreferences : HubEffect
}
