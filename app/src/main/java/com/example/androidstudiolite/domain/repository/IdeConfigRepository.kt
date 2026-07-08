package com.example.androidstudiolite.domain.repository

import com.example.androidstudiolite.domain.model.IdeConfigState
import kotlinx.coroutines.flow.Flow

interface IdeConfigRepository {
    fun observeState(): Flow<IdeConfigState>
    suspend fun installComponent(id: String)
    suspend fun setOfflineMode(enabled: Boolean)
    suspend fun setNetworkAvailable(available: Boolean)
}
