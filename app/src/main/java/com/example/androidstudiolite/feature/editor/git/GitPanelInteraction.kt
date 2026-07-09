package com.example.androidstudiolite.feature.editor.git
sealed interface GitPanelInteraction {
    data class SelectChange(val path: String) : GitPanelInteraction
    data object CloseDiff : GitPanelInteraction
    data class CommitMessageChanged(val message: String) : GitPanelInteraction
    data object Commit : GitPanelInteraction
}
