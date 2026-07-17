package com.ahmadkharfan.androidstudiolite.feature.settings.server

interface ServerSettingsInteractionListener {
    fun onBaseUrlChanged(url: String)
    fun onSaveBaseUrl()
    fun onRegisterDevice()
    fun onClearToken()
    fun onGitAuthorNameChanged(name: String)
    fun onGitAuthorEmailChanged(email: String)
    fun onSaveGitAuthor()
}
