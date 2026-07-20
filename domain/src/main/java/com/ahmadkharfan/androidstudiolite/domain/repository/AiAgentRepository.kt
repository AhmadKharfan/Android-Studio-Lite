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

    /** Sets the default model for [providerId]. */
    suspend fun setModel(providerId: String, model: String)

    /** Sets the default provider for new chats. */
    suspend fun setActiveProvider(providerId: String)

    /** Fetches the live model list for [providerId] and caches it for the settings UI. */
    suspend fun refreshModels(providerId: String)
}
