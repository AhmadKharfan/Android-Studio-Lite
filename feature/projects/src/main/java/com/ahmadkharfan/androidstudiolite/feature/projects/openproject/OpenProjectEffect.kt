package com.ahmadkharfan.androidstudiolite.feature.projects.openproject

sealed interface OpenProjectEffect {
    data class NavigateToProject(val id: String) : OpenProjectEffect
}
