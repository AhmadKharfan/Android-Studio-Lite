package com.example.androidstudiolite.feature.settings.root
sealed interface SettingsRootInteraction {
    data class QueryChanged(val query: String) : SettingsRootInteraction
}
