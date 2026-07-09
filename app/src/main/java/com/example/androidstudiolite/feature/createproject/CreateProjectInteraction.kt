package com.example.androidstudiolite.feature.createproject
sealed interface CreateProjectInteraction {
    data class SelectTemplate(val id: String) : CreateProjectInteraction
    data object NextStep : CreateProjectInteraction
    data object BackStep : CreateProjectInteraction
    data class NameChanged(val name: String) : CreateProjectInteraction
    data class PackageChanged(val packageName: String) : CreateProjectInteraction
    data class LocationChanged(val location: String) : CreateProjectInteraction
    data class MinSdkChanged(val minSdk: String) : CreateProjectInteraction
    data object CreateProject : CreateProjectInteraction
}
