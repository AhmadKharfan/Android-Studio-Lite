package com.example.androidstudiolite.feature.openproject
sealed interface OpenProjectInteraction {
    data class QueryChanged(val query: String) : OpenProjectInteraction
    data class SelectProject(val id: String) : OpenProjectInteraction
}
