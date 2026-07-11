package com.example.androidstudiolite.feature.settings.aiagent

import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.core.BaseViewModel
import com.example.androidstudiolite.domain.model.AiProviderConfig
import com.example.androidstudiolite.domain.repository.AiAgentRepository
import kotlinx.coroutines.launch

class AiAgentViewModel(
    private val aiAgentRepository: AiAgentRepository,
) : BaseViewModel<AiAgentUiState, Nothing>(initialState = AiAgentUiState()), AiAgentInteractionListener {

    init {
        tryToCollect(
            block = { aiAgentRepository.observeSettings() },
            onCollect = { settings ->
                updateState {
                    copy(
                        enabled = settings.enabled,
                        providers = settings.providers.map { it.toUiModel() },
                        instructions = settings.instructions,
                    )
                }
            },
        )
    }

    override fun onToggleEnabled(enabled: Boolean) {
        viewModelScope.launch { aiAgentRepository.setEnabled(enabled) }
    }

    override fun onApiKeyChanged(providerId: String, key: String) {
        viewModelScope.launch { aiAgentRepository.setApiKey(providerId, key) }
    }

    override fun onTestApiKey(providerId: String) {
        viewModelScope.launch { aiAgentRepository.testApiKey(providerId) }
    }

    override fun onInstructionsChanged(instructions: String) {
        viewModelScope.launch { aiAgentRepository.setInstructions(instructions) }
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
