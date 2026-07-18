package com.ahmadkharfan.androidstudiolite.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.ahmadkharfan.androidstudiolite.data.ai.AiAgentPreferencesStore
import com.ahmadkharfan.androidstudiolite.data.ai.AiLlmGateway
import com.ahmadkharfan.androidstudiolite.data.ai.EncryptedAiKeyStore
import com.ahmadkharfan.androidstudiolite.data.ai.RealAiAgentRepository
import com.ahmadkharfan.androidstudiolite.data.ai.RealAiChatRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.AiAgentRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.AiChatRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val Context.aiAgentDataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_agent_preferences")

val aiModule = module {
    single { EncryptedAiKeyStore(androidContext()) }
    single { AiAgentPreferencesStore(androidContext().aiAgentDataStore) }
    single { AiLlmGateway() }
    single<AiAgentRepository> { RealAiAgentRepository(get(), get(), get()) }
    single<AiChatRepository> { RealAiChatRepository(get(), get(), get()) }
}
