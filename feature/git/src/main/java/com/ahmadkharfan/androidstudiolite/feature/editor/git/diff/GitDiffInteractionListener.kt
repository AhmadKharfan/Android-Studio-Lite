package com.ahmadkharfan.androidstudiolite.feature.editor.git.diff

import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffHunk

interface GitDiffInteractionListener {
    fun setSideBySide(enabled: Boolean)
    fun showAnyway()
    fun stage(hunk: GitDiffHunk)
    fun unstage(hunk: GitDiffHunk)
}
