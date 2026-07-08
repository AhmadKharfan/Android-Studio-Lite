package com.example.androidstudiolite.feature.splash.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.di.AppContainer
import com.example.androidstudiolite.domain.usecase.ObserveOnboardingStateUseCase
import com.example.androidstudiolite.feature.splash.uiState.SplashUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val SPLASH_MIN_DURATION_MS = 900L

class SplashViewModel(
    private val observeOnboardingState: ObserveOnboardingStateUseCase = ObserveOnboardingStateUseCase(AppContainer.onboardingRepository),
) : ViewModel() {

    private val _uiState = MutableStateFlow<SplashUiState>(SplashUiState.Loading)
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            delay(SPLASH_MIN_DURATION_MS)
            val onboardingComplete = observeOnboardingState().first().onboardingComplete
            _uiState.value = if (onboardingComplete) SplashUiState.NavigateToHub else SplashUiState.NavigateToOnboarding
        }
    }
}
