package com.ahmadkharfan.androidstudiolite.feature.folderpicker

interface FolderPickerInteractionListener {
    fun onToggleFolder(id: String)
    fun onSelectFolder(id: String)
    fun onStartCreateFolder()
    fun onCancelCreateFolder()
    fun onNewFolderNameChanged(name: String)
    fun onConfirmCreateFolder()
}
