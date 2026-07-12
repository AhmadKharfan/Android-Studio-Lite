package com.example.androidstudiolite.domain.repository

import com.example.androidstudiolite.domain.model.OnboardingState
import kotlinx.coroutines.flow.Flow

interface OnboardingRepository {
    fun observeState(): Flow<OnboardingState>

    /**
     * Re-reads every permission's real, live state from the OS (PackageManager / Environment) and
     * publishes it on [observeState]. Permissions are never cached as "granted" — the user can revoke
     * them from Settings at any time, so every check goes straight to the system.
     */
    suspend fun refreshPermissions()
    suspend fun markSetupComplete()
    suspend fun completeOnboarding()
}
