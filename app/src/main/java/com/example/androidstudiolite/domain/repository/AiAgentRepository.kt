package com.example.androidstudiolite.domain.repository

import com.example.androidstudiolite.domain.model.AiAgentSettings
import kotlinx.coroutines.flow.Flow

interface AiAgentRepository {
    fun observeSettings(): Flow<AiAgentSettings>
    suspend fun setEnabled(enabled: Boolean)
    suspend fun setApiKey(providerId: String, key: String)
    suspend fun testApiKey(providerId: String)
    suspend fun setInstructions(instructions: String)
}
