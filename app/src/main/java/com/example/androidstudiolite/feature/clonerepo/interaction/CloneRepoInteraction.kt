package com.example.androidstudiolite.feature.clonerepo.interaction

sealed interface CloneRepoInteraction {
    data class UrlChanged(val url: String) : CloneRepoInteraction
    data class BranchChanged(val branch: String) : CloneRepoInteraction
    data class ToggleOption(val id: String) : CloneRepoInteraction
    data object StartClone : CloneRepoInteraction
}
