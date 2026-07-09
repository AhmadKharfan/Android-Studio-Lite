package com.example.androidstudiolite.feature.folderpicker
sealed interface FolderPickerInteraction {
    data class ToggleFolder(val id: String) : FolderPickerInteraction
    data class SelectFolder(val id: String) : FolderPickerInteraction
}
