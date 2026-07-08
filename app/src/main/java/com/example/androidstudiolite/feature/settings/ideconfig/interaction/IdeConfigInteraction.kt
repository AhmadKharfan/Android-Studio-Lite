package com.example.androidstudiolite.feature.settings.ideconfig.interaction

sealed interface IdeConfigInteraction {
    data class InstallComponent(val id: String) : IdeConfigInteraction
    data class ToggleOfflineMode(val enabled: Boolean) : IdeConfigInteraction
    data object RetryConnection : IdeConfigInteraction
}
