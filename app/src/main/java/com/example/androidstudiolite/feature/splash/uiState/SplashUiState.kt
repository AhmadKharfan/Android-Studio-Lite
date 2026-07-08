package com.example.androidstudiolite.feature.splash.uiState

sealed interface SplashUiState {
    data object Loading : SplashUiState
    data object NavigateToOnboarding : SplashUiState
    data object NavigateToHub : SplashUiState
}
