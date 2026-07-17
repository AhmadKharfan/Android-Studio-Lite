package com.ahmadkharfan.androidstudiolite.feature.clonerepo

interface CloneRepoInteractionListener {
    fun onUrlChanged(url: String)
    fun onBranchChanged(branch: String)
    fun onTokenChanged(token: String)
    fun onToggleOption(id: String)
    fun onStartClone()
    fun onCancelClone()
}
