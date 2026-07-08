package com.example.androidstudiolite.feature.settings.developer.interaction

sealed interface DeveloperOptionsInteraction {
    data class ToggleSimulateOffline(val enabled: Boolean) : DeveloperOptionsInteraction
}
