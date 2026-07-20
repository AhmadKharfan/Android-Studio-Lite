package com.ahmadkharfan.androidstudiolite.feature.editor.git.conflict

interface GitConflictInteractionListener {
    fun acceptOurs(path: String)
    fun acceptTheirs(path: String)
    fun markResolved(path: String, allowMarkers: Boolean = false)
    fun dismissMarkerWarning()
}
