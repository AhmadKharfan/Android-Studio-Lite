package com.example.androidstudiolite.domain.usecase

import com.example.androidstudiolite.domain.model.OnboardingState
import com.example.androidstudiolite.domain.repository.OnboardingRepository
import kotlinx.coroutines.flow.Flow

class ObserveOnboardingStateUseCase(private val repository: OnboardingRepository) {
    operator fun invoke(): Flow<OnboardingState> = repository.observeState()
}

class UpdatePermissionUseCase(private val repository: OnboardingRepository) {
    suspend operator fun invoke(id: String, granted: Boolean) = repository.setPermissionGranted(id, granted)
}

class MarkSetupCompleteUseCase(private val repository: OnboardingRepository) {
    suspend operator fun invoke() = repository.markSetupComplete()
}

class CompleteOnboardingUseCase(private val repository: OnboardingRepository) {
    suspend operator fun invoke() = repository.completeOnboarding()
}
