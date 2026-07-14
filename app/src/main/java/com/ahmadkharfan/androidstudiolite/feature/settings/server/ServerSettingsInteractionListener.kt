package com.ahmadkharfan.androidstudiolite.feature.settings.server

interface ServerSettingsInteractionListener {
    fun onBaseUrlChanged(url: String)
    fun onSaveBaseUrl()
    fun onRegisterDevice()
    fun onClearToken()
}
