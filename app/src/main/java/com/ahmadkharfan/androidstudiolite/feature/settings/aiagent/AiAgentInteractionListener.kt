package com.ahmadkharfan.androidstudiolite.feature.settings.aiagent

interface AiAgentInteractionListener {
    fun onToggleEnabled(enabled: Boolean)
    fun onApiKeyChanged(providerId: String, key: String)
    fun onTestApiKey(providerId: String)
    fun onInstructionsChanged(instructions: String)
    fun onToggleAutoApply(enabled: Boolean)
    fun onModelChanged(providerId: String, model: String)
    fun onRefreshModels(providerId: String)
}
