package com.example.androidstudiolite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.androidstudiolite.designsystem.theme.AslAppTheme
import com.example.androidstudiolite.domain.model.AppPreferences
import com.example.androidstudiolite.domain.model.AppThemeMode
import com.example.androidstudiolite.domain.repository.OnboardingRepository
import com.example.androidstudiolite.domain.repository.PreferencesRepository
import com.example.androidstudiolite.navigation.AslNavHost
import com.example.androidstudiolite.navigation.Routes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val preferencesRepository: PreferencesRepository by inject()
    private val onboardingRepository: OnboardingRepository by inject()
    private var startDestination by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition { startDestination == null }

        lifecycleScope.launch {
            val onboardingComplete = onboardingRepository.observeState().first().onboardingComplete
            startDestination = if (onboardingComplete) Routes.HUB else Routes.ONBOARDING_WELCOME
        }

        enableEdgeToEdge()
        setContent {
            val destination = startDestination ?: return@setContent
            val preferences by preferencesRepository.observePreferences()
                .collectAsStateWithLifecycle(initialValue = AppPreferences())
            val darkTheme = when (preferences.themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            AslAppTheme(darkTheme = darkTheme) {
                AslNavHost(startDestination = destination)
            }
        }
    }
}
