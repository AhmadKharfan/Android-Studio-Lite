package com.ahmadkharfan.androidstudiolite.feature.settings.gitauth

import com.ahmadkharfan.androidstudiolite.feature.editor.git.GitAuthPromptActions

interface GitAuthSettingsInteractionListener : GitAuthPromptActions {
    fun onConnectGitHub()
    fun onDisconnectGitHub()
    fun onGitAuthorNameChanged(name: String)
    fun onGitAuthorEmailChanged(email: String)
    fun onSaveGitAuthor()
    fun onStatusMessageShown()
}
