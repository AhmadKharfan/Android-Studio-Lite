package com.example.androidstudiolite.feature.settings.aiagent
sealed interface AiAgentInteraction {
    data class ToggleEnabled(val enabled: Boolean) : AiAgentInteraction
    data class ApiKeyChanged(val providerId: String, val key: String) : AiAgentInteraction
    data class TestApiKey(val providerId: String) : AiAgentInteraction
    data class InstructionsChanged(val instructions: String) : AiAgentInteraction
}
