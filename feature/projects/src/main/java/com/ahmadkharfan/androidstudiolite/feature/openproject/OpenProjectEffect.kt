package com.ahmadkharfan.androidstudiolite.feature.openproject

sealed interface OpenProjectEffect {
    data class NavigateToProject(val id: String) : OpenProjectEffect
}
