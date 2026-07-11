package com.example.androidstudiolite.feature.settings.aiagent

interface AiAgentInteractionListener {
    fun onToggleEnabled(enabled: Boolean)
    fun onApiKeyChanged(providerId: String, key: String)
    fun onTestApiKey(providerId: String)
    fun onInstructionsChanged(instructions: String)
}
