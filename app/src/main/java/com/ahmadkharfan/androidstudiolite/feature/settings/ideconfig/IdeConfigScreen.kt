package com.ahmadkharfan.androidstudiolite.feature.settings.ideconfig
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslListItem
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

/**
 * Explains where builds happen and points at Server settings.
 *
 * This screen used to install a JDK/SDK/Gradle toolchain onto the device, with per-component
 * download/verify/extract progress. Builds now run on the remote worker (`data/remote/`), so there is
 * no on-device toolchain to configure — the install machinery went with it, and what's left is this
 * signpost so the entry point doesn't dead-end.
 */
@Composable
fun IdeConfigRoute(onBack: () -> Unit, onOpenServerSettings: () -> Unit) {
    val colors = AslTheme.colors
    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AslTopAppBar(title = "IDE Configurations", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, AslShape.lg)
                        .border(1.dp, colors.borderDefault, AslShape.lg)
                        .padding(4.dp),
                ) {
                    AslListItem(
                        title = "Builds run on the cloud service",
                        subtitle = "No JDK, Android SDK or Gradle install is needed on this device.",
                        icon = "cloud",
                        divider = false,
                    )
                }
                Text(
                    text = "Your project is uploaded to the build server, built there, and the APK " +
                        "comes back for install. Point the app at a different server, or check the " +
                        "one it's using, in Server settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                AslButton(
                    label = "Server settings",
                    icon = "server",
                    onClick = onOpenServerSettings,
                    variant = AslButtonVariant.Secondary,
                )
            }
        }
    }
}
