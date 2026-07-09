package com.example.androidstudiolite.feature.crashreport
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.designsystem.component.buttons.AslButton
import com.example.androidstudiolite.designsystem.component.buttons.AslButtonSize
import com.example.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.example.androidstudiolite.designsystem.component.feedback.AslSnackbar
import com.example.androidstudiolite.designsystem.icon.AslIcon
import com.example.androidstudiolite.designsystem.theme.AslCode
import com.example.androidstudiolite.designsystem.theme.AslShape
import com.example.androidstudiolite.designsystem.theme.AslTheme

private const val STACK_TRACE = """java.lang.OutOfMemoryError:
  Failed to allocate 48MB
  at o.gradle.DaemonMain.run
  at o.lsp.KotlinServer.index
  at a.os.Handler.dispatchMessage
  ...12 more"""

@Composable
fun CrashReportRoute(onRestart: () -> Unit, onClose: () -> Unit) {
    CrashReportScreen(onRestart = onRestart, onClose = onClose)
}

@Composable
private fun CrashReportScreen(onRestart: () -> Unit, onClose: () -> Unit) {
    val colors = AslTheme.colors
    val clipboard = LocalClipboardManager.current
    var showCopiedSnackbar by remember { mutableStateOf(false) }

    LaunchedEffect(showCopiedSnackbar) {
        if (showCopiedSnackbar) {
            kotlinx.coroutines.delay(2000)
            showCopiedSnackbar = false
        }
    }

    Scaffold(containerColor = colors.bgBase) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(colors.warningContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    AslIcon(name = "life-buoy", size = 28.dp, tint = colors.warning)
                }
                Column {
                    Text(
                        text = "Something went wrong",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "Android Studio Lite encountered an error and needs to restart. Your files are saved.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Text(
                    text = STACK_TRACE,
                    style = AslCode.codeTiny,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .background(colors.bgSunken, AslShape.sm)
                        .border(1.dp, colors.borderSubtle, AslShape.sm)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .verticalScroll(rememberScrollState()),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AslButton(
                        label = "Restart IDE",
                        onClick = onRestart,
                        variant = AslButtonVariant.Primary,
                        size = AslButtonSize.Lg,
                        icon = "rotate-ccw",
                        fullWidth = true,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AslButton(
                            label = "Copy report",
                            onClick = {
                                clipboard.setText(AnnotatedString(STACK_TRACE))
                                showCopiedSnackbar = true
                            },
                            variant = AslButtonVariant.Secondary,
                            icon = "copy",
                            fullWidth = true,
                            modifier = Modifier.weight(1f),
                        )
                        AslButton(
                            label = "Close",
                            onClick = onClose,
                            variant = AslButtonVariant.Tertiary,
                            fullWidth = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = showCopiedSnackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp),
            ) {
                AslSnackbar(message = "Report copied to clipboard", icon = "check-circle")
            }
        }
    }
}
