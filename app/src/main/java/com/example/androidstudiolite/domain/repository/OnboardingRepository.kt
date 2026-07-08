package com.example.androidstudiolite.domain.repository

import com.example.androidstudiolite.domain.model.OnboardingState
import kotlinx.coroutines.flow.Flow

interface OnboardingRepository {
    fun observeState(): Flow<OnboardingState>
    suspend fun setPermissionGranted(id: String, granted: Boolean)
    suspend fun markSetupComplete()
    suspend fun completeOnboarding()
}
