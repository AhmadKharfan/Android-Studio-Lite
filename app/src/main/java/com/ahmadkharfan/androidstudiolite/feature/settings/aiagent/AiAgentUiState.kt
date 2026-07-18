package com.ahmadkharfan.androidstudiolite.feature.settings.aiagent
import androidx.compose.runtime.Immutable

import com.ahmadkharfan.androidstudiolite.domain.model.ApiKeyStatus

@Immutable
data class AiProviderUiModel(
    val id: String,
    val name: String,
    val icon: String,
    val description: String,
    val apiKey: String,
    val status: ApiKeyStatus,
    val featured: Boolean,
    val keyError: String? = null,
    val availableModels: List<String> = emptyList(),
    val selectedModel: String = "",
)

@Immutable
data class AiAgentUiState(
    val enabled: Boolean = true,
    val providers: List<AiProviderUiModel> = emptyList(),
    val instructions: String = "",
    val autoApply: Boolean = false,
)
