package com.ahmadkharfan.androidstudiolite.feature.terminal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonSize
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import androidx.compose.material3.MaterialTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalSettingsSheet(
    linux: LinuxStatus,
    onDismiss: () -> Unit,
    onInstallLinux: () -> Unit,
    onReinstallLinux: () -> Unit,
) {
    val colors = AslTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bgElevated,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Terminal settings", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(
                text = "Volume Up/Down moves the browse cursor one character at a time. Tap the terminal to return to live input.",
                style = AslCode.codeSmall,
                color = colors.textSecondary,
            )
            if (linux.supported) {
                Text(
                    text = when {
                        linux.installed -> "Linux userland: installed (Alpine via proot)"
                        linux.isBusy -> linux.phase ?: "Installing Linux userland…"
                        else -> "Linux userland: not installed — install to run apk, git, python, gcc, etc."
                    },
                    style = AslCode.codeSmall,
                    color = colors.textPrimary,
                )
                linux.error?.let {
                    Text(text = "Last install error: $it", style = AslCode.codeTiny, color = colors.error)
                }
                if (!linux.installed && !linux.isBusy) {
                    AslButton(
                        label = if (linux.error != null) "Retry install" else "Install Linux userland",
                        onClick = onInstallLinux,
                        variant = AslButtonVariant.Primary,
                        size = AslButtonSize.Md,
                        icon = "download",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (linux.installed && !linux.isBusy) {
                    Text(
                        text = "Install tools inside the shell, e.g. apk add git. Projects are at /root/projects.",
                        style = AslCode.codeTiny,
                        color = colors.textSecondary,
                    )
                    AslButton(
                        label = "Reinstall Linux userland",
                        onClick = onReinstallLinux,
                        variant = AslButtonVariant.Secondary,
                        size = AslButtonSize.Md,
                        icon = "refresh-cw",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Text(
                    text = "Full Linux userland is not available on this device architecture.",
                    style = AslCode.codeSmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}
