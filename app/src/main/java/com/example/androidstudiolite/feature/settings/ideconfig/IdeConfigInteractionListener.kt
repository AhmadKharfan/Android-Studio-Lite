package com.example.androidstudiolite.feature.settings.ideconfig

interface IdeConfigInteractionListener {
    fun onInstallComponent(id: String)
    fun onRetryConnection()
}
