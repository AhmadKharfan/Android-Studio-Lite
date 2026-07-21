package com.ahmadkharfan.androidstudiolite.feature.settings.aiagent

import androidx.lifecycle.viewModelScope
import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.domain.model.AiProviderConfig
import com.ahmadkharfan.androidstudiolite.domain.repository.AiAgentRepository
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
                        autoApply = settings.autoApply,
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

    override fun onTestApiKey(providerId: String, apiKey: String?) {
        viewModelScope.launch {
            if (apiKey != null) aiAgentRepository.setApiKey(providerId, apiKey)
            aiAgentRepository.testApiKey(providerId)
        }
    }

    override fun onInstructionsChanged(instructions: String) {
        viewModelScope.launch { aiAgentRepository.setInstructions(instructions) }
    }

    override fun onToggleAutoApply(enabled: Boolean) {
        viewModelScope.launch { aiAgentRepository.setAutoApply(enabled) }
    }

    override fun onModelChanged(providerId: String, model: String) {
        viewModelScope.launch { aiAgentRepository.setModel(providerId, model) }
    }

    override fun onRefreshModels(providerId: String) {
        viewModelScope.launch { aiAgentRepository.refreshModels(providerId) }
    }

    private fun AiProviderConfig.toUiModel() = AiProviderUiModel(
        id = id,
        name = name,
        icon = icon,
        description = description,
        apiKey = apiKey,
        status = status,
        featured = featured,
        keyError = keyError,
        availableModels = availableModels,
        selectedModel = selectedModel,
    )
}
