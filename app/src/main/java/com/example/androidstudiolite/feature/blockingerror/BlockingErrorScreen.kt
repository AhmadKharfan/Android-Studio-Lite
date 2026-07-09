package com.example.androidstudiolite.feature.blockingerror
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.androidstudiolite.designsystem.component.content.AslErrorState
import com.example.androidstudiolite.designsystem.theme.AslTheme

enum class BlockingErrorType { UnsupportedDevice, SdCardInstall, SecondaryUser }

@Composable
fun BlockingErrorRoute(type: BlockingErrorType) {
    BackHandler(enabled = true) {} // blocking screen — no back navigation, only the one offered action
    val context = LocalContext.current
    val activity = context as? Activity
    BlockingErrorScreen(
        type = type,
        onAction = {
            when (type) {
                BlockingErrorType.SdCardInstall -> context.startActivity(Intent(Settings.ACTION_SETTINGS))
                BlockingErrorType.UnsupportedDevice, BlockingErrorType.SecondaryUser -> activity?.finish()
            }
        },
    )
}

@Composable
private fun BlockingErrorScreen(type: BlockingErrorType, onAction: () -> Unit) {
    val colors = AslTheme.colors
    Scaffold(containerColor = colors.bgBase) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when (type) {
                BlockingErrorType.UnsupportedDevice -> AslErrorState(
                    icon = "smartphone",
                    title = "Device not supported",
                    explanation = "Android Studio Lite requires a 64-bit device running Android 8.0 or newer.",
                    actionLabel = "Exit",
                    onAction = onAction,
                    secondaryLabel = "Open docs",
                )
                BlockingErrorType.SdCardInstall -> AslErrorState(
                    icon = "hard-drive",
                    title = "Move app to internal storage",
                    explanation = "Gradle builds can't run from an SD card. Move the app to internal storage in system settings.",
                    actionLabel = "Open settings",
                    onAction = onAction,
                    secondaryLabel = "Open docs",
                )
                BlockingErrorType.SecondaryUser -> AslErrorState(
                    icon = "users",
                    title = "Primary user required",
                    explanation = "Android restricts app installs for secondary users. Switch to the primary user to continue.",
                    actionLabel = "Exit",
                    onAction = onAction,
                    secondaryLabel = "Open docs",
                )
            }
        }
    }
}
