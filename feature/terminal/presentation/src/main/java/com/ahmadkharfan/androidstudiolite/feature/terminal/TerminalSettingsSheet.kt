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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonSize
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTypography

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
            Text(text = stringResource(R.string.terminal_settings_title), style = AslTypography.titleMedium, color = colors.textPrimary)
            Text(
                text = stringResource(R.string.terminal_settings_volume_hint),
                style = AslCode.codeSmall,
                color = colors.textSecondary,
            )
            if (linux.supported) {
                Text(
                    text = when {
                        linux.installed -> stringResource(R.string.terminal_linux_installed)
                        linux.isBusy -> linux.phaseText() ?: stringResource(R.string.terminal_linux_installing_userland)
                        else -> stringResource(R.string.terminal_linux_not_installed)
                    },
                    style = AslCode.codeSmall,
                    color = colors.textPrimary,
                )
                linux.error?.let {
                    Text(text = stringResource(R.string.terminal_last_install_error, it), style = AslCode.codeTiny, color = colors.error)
                }
                if (!linux.installed && !linux.isBusy) {
                    AslButton(
                        label = stringResource(if (linux.error != null) R.string.terminal_retry_install else R.string.terminal_install_userland),
                        onClick = onInstallLinux,
                        variant = AslButtonVariant.Primary,
                        size = AslButtonSize.Md,
                        icon = "download",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (linux.installed && !linux.isBusy) {
                    Text(
                        text = stringResource(R.string.terminal_linux_tools_hint),
                        style = AslCode.codeTiny,
                        color = colors.textSecondary,
                    )
                    AslButton(
                        label = stringResource(R.string.terminal_reinstall_userland),
                        onClick = onReinstallLinux,
                        variant = AslButtonVariant.Secondary,
                        size = AslButtonSize.Md,
                        icon = "refresh-cw",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.terminal_linux_unsupported),
                    style = AslCode.codeSmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}
