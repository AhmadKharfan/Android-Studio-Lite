package com.example.androidstudiolite.domain.usecase

import com.example.androidstudiolite.domain.model.AiAgentSettings
import com.example.androidstudiolite.domain.repository.AiAgentRepository
import kotlinx.coroutines.flow.Flow

class ObserveAiAgentSettingsUseCase(private val repository: AiAgentRepository) {
    operator fun invoke(): Flow<AiAgentSettings> = repository.observeSettings()
}

class SetAiAgentEnabledUseCase(private val repository: AiAgentRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setEnabled(enabled)
}

class SetAiProviderApiKeyUseCase(private val repository: AiAgentRepository) {
    suspend operator fun invoke(providerId: String, key: String) = repository.setApiKey(providerId, key)
}

class TestAiProviderApiKeyUseCase(private val repository: AiAgentRepository) {
    suspend operator fun invoke(providerId: String) = repository.testApiKey(providerId)
}

class SetAiAgentInstructionsUseCase(private val repository: AiAgentRepository) {
    suspend operator fun invoke(instructions: String) = repository.setInstructions(instructions)
}
