package com.ahmadkharfan.androidstudiolite.feature.editor.git

interface GitPanelInteractionListener {
    fun onSelectChange(path: String)
    fun onCloseDiff()
    fun onStage(path: String)
    fun onUnstage(path: String)
    fun onCommitMessageChanged(message: String)
    fun onCommit()
    fun onPush()
    fun onPull()
    fun onRefresh()
    fun onStatusMessageShown()
}
