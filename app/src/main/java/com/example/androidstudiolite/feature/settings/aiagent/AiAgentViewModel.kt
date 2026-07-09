package com.example.androidstudiolite.feature.settings.aiagent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.domain.model.AiProviderConfig
import com.example.androidstudiolite.domain.repository.AiAgentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AiAgentViewModel(
    private val aiAgentRepository: AiAgentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiAgentUiState())
    val uiState: StateFlow<AiAgentUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            aiAgentRepository.observeSettings().collect { settings ->
                _uiState.value = AiAgentUiState(
                    enabled = settings.enabled,
                    providers = settings.providers.map { it.toUiModel() },
                    instructions = settings.instructions,
                )
            }
        }
    }

    fun onInteraction(interaction: AiAgentInteraction) {
        viewModelScope.launch {
            when (interaction) {
                is AiAgentInteraction.ToggleEnabled -> aiAgentRepository.setEnabled(interaction.enabled)
                is AiAgentInteraction.ApiKeyChanged -> aiAgentRepository.setApiKey(interaction.providerId, interaction.key)
                is AiAgentInteraction.TestApiKey -> aiAgentRepository.testApiKey(interaction.providerId)
                is AiAgentInteraction.InstructionsChanged -> aiAgentRepository.setInstructions(interaction.instructions)
            }
        }
    }

    private fun AiProviderConfig.toUiModel() = AiProviderUiModel(
        id = id,
        name = name,
        icon = icon,
        description = description,
        apiKey = apiKey,
        status = status,
        featured = featured,
    )
}
