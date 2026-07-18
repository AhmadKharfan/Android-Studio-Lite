package com.ahmadkharfan.androidstudiolite.feature.settings.gitauth

import com.ahmadkharfan.androidstudiolite.feature.editor.git.GitAuthPromptActions

interface GitAuthSettingsInteractionListener : GitAuthPromptActions {
    /** Opens the GitHub sign-in / token dialog. */
    fun onConnectGitHub()
    /** Removes the stored github.com credentials. */
    fun onDisconnectGitHub()
    fun onGitAuthorNameChanged(name: String)
    fun onGitAuthorEmailChanged(email: String)
    fun onSaveGitAuthor()
    fun onStatusMessageShown()
}
