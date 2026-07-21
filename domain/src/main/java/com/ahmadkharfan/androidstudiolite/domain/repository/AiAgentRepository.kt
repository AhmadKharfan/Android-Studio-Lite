package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.AiAgentSettings
import kotlinx.coroutines.flow.Flow

interface AiAgentRepository {
    fun observeSettings(): Flow<AiAgentSettings>
    suspend fun setEnabled(enabled: Boolean)
    suspend fun setApiKey(providerId: String, key: String)
    suspend fun testApiKey(providerId: String)
    suspend fun setInstructions(instructions: String)
    suspend fun setAutoApply(enabled: Boolean)

    suspend fun setModel(providerId: String, model: String)

    suspend fun setBaseUrl(providerId: String, url: String)

    suspend fun setActiveProvider(providerId: String)

    suspend fun refreshModels(providerId: String)
}
