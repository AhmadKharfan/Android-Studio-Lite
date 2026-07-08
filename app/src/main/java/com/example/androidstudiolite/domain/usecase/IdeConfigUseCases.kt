package com.example.androidstudiolite.domain.usecase

import com.example.androidstudiolite.domain.model.IdeConfigState
import com.example.androidstudiolite.domain.repository.IdeConfigRepository
import kotlinx.coroutines.flow.Flow

class ObserveIdeConfigStateUseCase(private val repository: IdeConfigRepository) {
    operator fun invoke(): Flow<IdeConfigState> = repository.observeState()
}

class InstallIdeComponentUseCase(private val repository: IdeConfigRepository) {
    suspend operator fun invoke(id: String) = repository.installComponent(id)
}

class SetOfflineModeUseCase(private val repository: IdeConfigRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setOfflineMode(enabled)
}

class SetNetworkAvailableUseCase(private val repository: IdeConfigRepository) {
    suspend operator fun invoke(available: Boolean) = repository.setNetworkAvailable(available)
}
