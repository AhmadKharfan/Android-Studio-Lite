package com.example.androidstudiolite.feature.onboarding.complete

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.domain.repository.OnboardingRepository
import kotlinx.coroutines.launch

class CompleteViewModel(
    private val onboardingRepository: OnboardingRepository,
) : ViewModel() {
    fun onFinish(onDone: () -> Unit) {
        viewModelScope.launch {
            onboardingRepository.completeOnboarding()
            onDone()
        }
    }
}
