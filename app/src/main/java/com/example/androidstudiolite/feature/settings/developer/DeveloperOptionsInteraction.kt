package com.example.androidstudiolite.feature.settings.developer
sealed interface DeveloperOptionsInteraction {
    data class ToggleSimulateOffline(val enabled: Boolean) : DeveloperOptionsInteraction
}
