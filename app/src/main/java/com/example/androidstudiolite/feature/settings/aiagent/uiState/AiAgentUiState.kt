package com.example.androidstudiolite.feature.settings.aiagent.uiState

import com.example.androidstudiolite.domain.model.ApiKeyStatus

data class AiProviderUiModel(
    val id: String,
    val name: String,
    val icon: String,
    val description: String,
    val apiKey: String,
    val status: ApiKeyStatus,
    val featured: Boolean,
)

data class AiAgentUiState(
    val enabled: Boolean = true,
    val providers: List<AiProviderUiModel> = emptyList(),
    val instructions: String = "",
)
