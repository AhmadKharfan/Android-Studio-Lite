package com.example.androidstudiolite.feature.openproject

sealed interface OpenProjectEffect {
    data class NavigateToProject(val id: String) : OpenProjectEffect
}
