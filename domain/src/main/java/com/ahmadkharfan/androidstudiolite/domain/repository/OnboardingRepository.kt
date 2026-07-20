package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.OnboardingState
import kotlinx.coroutines.flow.Flow

interface OnboardingRepository {
    fun observeState(): Flow<OnboardingState>

    suspend fun refreshPermissions()
    suspend fun markSetupComplete()
    suspend fun completeOnboarding()
}
