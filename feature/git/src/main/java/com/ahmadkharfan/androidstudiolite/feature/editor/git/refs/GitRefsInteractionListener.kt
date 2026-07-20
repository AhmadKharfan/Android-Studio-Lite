package com.ahmadkharfan.androidstudiolite.feature.editor.git.refs

import com.ahmadkharfan.androidstudiolite.domain.model.GitBranch
import com.ahmadkharfan.androidstudiolite.domain.model.PullMode
import com.ahmadkharfan.androidstudiolite.feature.editor.git.GitAuthPromptActions

interface GitRefsInteractionListener : GitAuthPromptActions {
    fun checkout(branch: GitBranch)
    fun createBranch(name: String)
    fun renameBranch(oldName: String, newName: String)
    fun deleteBranch(name: String, force: Boolean = false)
    fun dismissForceDelete()
    fun publish(name: String)
    fun merge(name: String)
    fun fetch()
    fun pull(mode: PullMode)
    fun push()
    fun dismissSyncMessage()
    fun createTag(name: String, message: String?)
    fun deleteTag(name: String)
    fun pushTag(name: String)
    fun pushAllTags()
    fun createStash(message: String?, includeUntracked: Boolean)
    fun applyStash(index: Int)
    fun popStash(index: Int)
    fun dropStash(index: Int)
}
