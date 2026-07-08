package com.example.androidstudiolite.feature.onboarding.complete.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.domain.usecase.CompleteOnboardingUseCase
import kotlinx.coroutines.launch

class CompleteViewModel(
    private val completeOnboarding: CompleteOnboardingUseCase,
) : ViewModel() {
    fun onFinish(onDone: () -> Unit) {
        viewModelScope.launch {
            completeOnboarding()
            onDone()
        }
    }
}
