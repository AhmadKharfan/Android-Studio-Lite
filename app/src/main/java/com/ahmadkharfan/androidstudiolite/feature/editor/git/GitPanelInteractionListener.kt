package com.ahmadkharfan.androidstudiolite.feature.editor.git

interface GitPanelInteractionListener {
    fun onSelectChange(path: String)
    fun onCloseDiff()
    fun onCommitMessageChanged(message: String)
    fun onCommit()
}
