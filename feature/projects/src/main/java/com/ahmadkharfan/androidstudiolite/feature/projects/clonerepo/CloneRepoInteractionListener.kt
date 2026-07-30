package com.ahmadkharfan.androidstudiolite.feature.projects.clonerepo

interface CloneRepoInteractionListener {
    fun onUrlChanged(url: String)
    fun onBranchChanged(branch: String)
    fun onToggleOption(id: String)
    fun onStartClone()
    fun onCancelClone()
}
