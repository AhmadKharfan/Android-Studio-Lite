package com.ahmadkharfan.androidstudiolite.feature.settings.ideconfig

interface IdeConfigInteractionListener {
    fun onInstallComponent(id: String)
    fun onRetryConnection()
}
