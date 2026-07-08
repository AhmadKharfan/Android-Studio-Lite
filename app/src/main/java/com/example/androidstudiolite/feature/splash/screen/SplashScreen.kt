package com.example.androidstudiolite.feature.splash.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.example.androidstudiolite.core.designsystem.component.navigation.AslSplash
import com.example.androidstudiolite.feature.splash.uiState.SplashUiState
import com.example.androidstudiolite.feature.splash.viewModel.SplashViewModel

@Composable
fun SplashRoute(
    onNavigateToOnboarding: () -> Unit,
    onNavigateToHub: () -> Unit,
    viewModel: SplashViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState) {
        when (uiState) {
            SplashUiState.NavigateToOnboarding -> onNavigateToOnboarding()
            SplashUiState.NavigateToHub -> onNavigateToHub()
            SplashUiState.Loading -> Unit
        }
    }

    SplashScreen()
}

@Composable
private fun SplashScreen(modifier: Modifier = Modifier) {
    AslSplash(modifier = modifier, version = "v1.0.0")
}
