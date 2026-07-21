package com.ahmadkharfan.androidstudiolite

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ahmadkharfan.androidstudiolite.data.buildsystem.install.PendingInstallConfirmation
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslAppTheme
import com.ahmadkharfan.androidstudiolite.domain.model.AppPreferences
import com.ahmadkharfan.androidstudiolite.domain.model.AppThemeMode
import com.ahmadkharfan.androidstudiolite.domain.repository.OnboardingRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.PreferencesRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import com.ahmadkharfan.androidstudiolite.feature.editor.view.EditorVolumeKeyDispatcher
import com.ahmadkharfan.androidstudiolite.feature.terminal.TerminalVolumeKeyDispatcher
import com.ahmadkharfan.androidstudiolite.navigation.AslNavHost
import com.ahmadkharfan.androidstudiolite.navigation.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val preferencesRepository: PreferencesRepository by inject()
    private val onboardingRepository: OnboardingRepository by inject()
    private val projectRepository: ProjectRepository by inject()
    private var startDestination by mutableStateOf<String?>(null)
    private var openProjectId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition { startDestination == null }
        openProjectId = intent.projectIdExtra()

        lifecycleScope.launch {
            val routing = withContext(Dispatchers.IO) {
                val onboardingComplete = onboardingRepository.observeState().first().onboardingComplete
                if (!onboardingComplete) {
                    return@withContext Routes.ONBOARDING_WELCOME to null
                }

                var projectId = openProjectId
                if (projectId == null) {
                    val prefs = preferencesRepository.observePreferences().first()
                    if (prefs.autoOpenLastProject) {
                        val last = projectRepository.observeRecentProjects().first()
                            .maxByOrNull { it.lastOpenedMillis ?: 0L }
                        if (last != null) projectId = last.id
                    }
                }
                Routes.HUB to projectId
            }
            openProjectId = routing.second
            startDestination = routing.first
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            val destination = startDestination ?: return@setContent
            val preferences by preferencesRepository.observePreferences()
                .collectAsStateWithLifecycle(initialValue = AppPreferences())
            val darkTheme = when (preferences.themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            LaunchedEffect(darkTheme) {
                preferencesRepository.ensureEditorThemeDefault(darkTheme)
            }
            AslAppTheme(darkTheme = darkTheme, accentId = preferences.accentId) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AslNavHost(
                        startDestination = destination,
                        openProjectId = openProjectId,
                        onOpenProjectConsumed = { openProjectId = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openProjectId = intent.projectIdExtra()
    }

    override fun onResume() {
        super.onResume()
        val confirmation = PendingInstallConfirmation.claimNext() ?: return
        runCatching { startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            EditorVolumeKeyDispatcher.handler?.invoke(event)?.takeIf { it }?.let { return true }
            TerminalVolumeKeyDispatcher.handler?.invoke(event)?.takeIf { it }?.let { return true }
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val EXTRA_OPEN_PROJECT_ID = "com.ahmadkharfan.androidstudiolite.OPEN_PROJECT_ID"

        fun openProjectIntent(context: Context, projectId: String): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
                if (projectId.isNotBlank()) {
                    putExtra(EXTRA_OPEN_PROJECT_ID, projectId)
                }
            }
    }
}

private fun Intent.projectIdExtra(): String? =
    getStringExtra(MainActivity.EXTRA_OPEN_PROJECT_ID)?.takeIf { it.isNotBlank() }
