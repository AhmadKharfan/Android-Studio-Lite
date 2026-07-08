package com.example.androidstudiolite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidstudiolite.core.designsystem.theme.AslAppTheme
import com.example.androidstudiolite.domain.model.AppPreferences
import com.example.androidstudiolite.domain.model.AppThemeMode
import com.example.androidstudiolite.domain.usecase.ObservePreferencesUseCase
import com.example.androidstudiolite.navigation.AslNavHost
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val observePreferences: ObservePreferencesUseCase by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences by observePreferences()
                .collectAsStateWithLifecycle(initialValue = AppPreferences())
            val darkTheme = when (preferences.themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            AslAppTheme(darkTheme = darkTheme) {
                AslNavHost()
            }
        }
    }
}
