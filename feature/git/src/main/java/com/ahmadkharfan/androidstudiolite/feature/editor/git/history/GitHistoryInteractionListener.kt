package com.ahmadkharfan.androidstudiolite.feature.editor.git.history

import com.ahmadkharfan.androidstudiolite.domain.model.GitResetMode

interface GitHistoryInteractionListener {
    fun loadNext()
    fun select(commitId: String)
    fun clearSelection()
    fun deepen()
    fun reset(commitId: String, mode: GitResetMode)
    fun toggleGraph()
}
