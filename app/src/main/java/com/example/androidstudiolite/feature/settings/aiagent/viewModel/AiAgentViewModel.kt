package com.example.androidstudiolite.feature.settings.aiagent.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.di.AppContainer
import com.example.androidstudiolite.domain.model.AiProviderConfig
import com.example.androidstudiolite.domain.usecase.ObserveAiAgentSettingsUseCase
import com.example.androidstudiolite.domain.usecase.SetAiAgentEnabledUseCase
import com.example.androidstudiolite.domain.usecase.SetAiAgentInstructionsUseCase
import com.example.androidstudiolite.domain.usecase.SetAiProviderApiKeyUseCase
import com.example.androidstudiolite.domain.usecase.TestAiProviderApiKeyUseCase
import com.example.androidstudiolite.feature.settings.aiagent.interaction.AiAgentInteraction
import com.example.androidstudiolite.feature.settings.aiagent.uiState.AiAgentUiState
import com.example.androidstudiolite.feature.settings.aiagent.uiState.AiProviderUiModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AiAgentViewModel(
    private val observeSettings: ObserveAiAgentSettingsUseCase = ObserveAiAgentSettingsUseCase(AppContainer.aiAgentRepository),
    private val setEnabled: SetAiAgentEnabledUseCase = SetAiAgentEnabledUseCase(AppContainer.aiAgentRepository),
    private val setApiKey: SetAiProviderApiKeyUseCase = SetAiProviderApiKeyUseCase(AppContainer.aiAgentRepository),
    private val testApiKey: TestAiProviderApiKeyUseCase = TestAiProviderApiKeyUseCase(AppContainer.aiAgentRepository),
    private val setInstructions: SetAiAgentInstructionsUseCase = SetAiAgentInstructionsUseCase(AppContainer.aiAgentRepository),
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiAgentUiState())
    val uiState: StateFlow<AiAgentUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeSettings().collect { settings ->
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
                is AiAgentInteraction.ToggleEnabled -> setEnabled(interaction.enabled)
                is AiAgentInteraction.ApiKeyChanged -> setApiKey(interaction.providerId, interaction.key)
                is AiAgentInteraction.TestApiKey -> testApiKey(interaction.providerId)
                is AiAgentInteraction.InstructionsChanged -> setInstructions(interaction.instructions)
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
