package com.ahmadkharfan.androidstudiolite.feature.terminal
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslHorizontalDivider
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslText
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.ahmadkharfan.androidstudiolite.core.common.R as CommonR
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslIconButton
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTextStyles


private val EXTRA_KEYS = listOf("Esc", "Tab", "Ctrl+C", "←", "↑", "↓", "→", "/", "|", "~", "-")

@Composable
fun TerminalRoute(onBack: () -> Unit, viewModel: TerminalViewModel = koinViewModel()) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    TerminalScreen(
        uiState = uiState,
        interactionListener = viewModel,
        onBack = onBack,
    )
    if (uiState.settingsVisible) {
        val listener: TerminalInteractionListener = viewModel
        TerminalSettingsSheet(
            linux = uiState.linux,
            onDismiss = listener::onDismissSettings,
            onInstallLinux = {
                listener.onDismissSettings()
                listener.onInstallLinux()
            },
            onReinstallLinux = {
                listener.onDismissSettings()
                listener.onReinstallLinux()
            },
        )
    }
}

@Composable
private fun TerminalScreen(
    uiState: TerminalUiState,
    interactionListener: TerminalInteractionListener,
    onBack: () -> Unit,
) {
    val colors = AslTheme.colors
    AslScaffold(containerColor = colors.bgBase) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TerminalTopBar(interactionListener = interactionListener, onBack = onBack, colors = colors)
            TerminalTabStrip(uiState = uiState, interactionListener = interactionListener, colors = colors)
            TerminalLinuxBanner(linux = uiState.linux, onInstall = { interactionListener.onInstallLinux() }, colors = colors)
            TerminalEmulatorView(
                screen = uiState.screen,
                background = colors.terminalBg,
                foreground = colors.terminalStdout,
                cursorColor = colors.terminalPrompt,
                onKey = { interactionListener.onKeyInput(it) },
                onSpecialKey = { interactionListener.onSpecialKey(it) },
                onResize = { rows, cols -> interactionListener.onResize(rows, cols) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            TerminalExtraKeysRow(interactionListener = interactionListener, colors = colors)
        }
    }
}

@Composable
private fun TerminalTopBar(
    interactionListener: TerminalInteractionListener,
    onBack: () -> Unit,
    colors: AslColorScheme,
) {
    Column(modifier = Modifier.background(colors.bgElevated)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AslIconButton(icon = "arrow-left", contentDescription = stringResource(CommonR.string.action_back), onClick = onBack)
            AslText(
                text = stringResource(R.string.terminal_title),
                style = AslTextStyles.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            AslIconButton(icon = "plus", contentDescription = stringResource(R.string.terminal_new_session), onClick = { interactionListener.onNewSession() })
            AslIconButton(icon = "settings-2", contentDescription = stringResource(R.string.terminal_settings_title), onClick = { interactionListener.onOpenSettings() })
        }
        AslHorizontalDivider(color = colors.borderDefault, thickness = 1.dp)
    }
}

@Composable
private fun TerminalTabStrip(
    uiState: TerminalUiState,
    interactionListener: TerminalInteractionListener,
    colors: AslColorScheme,
) {
    if (uiState.tabs.isEmpty()) return
    Column(modifier = Modifier.background(colors.bgElevated)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            uiState.tabs.forEach { tab ->
                TerminalTabChip(
                    tab = tab,
                    active = tab.id == uiState.activeTabId,
                    canClose = uiState.tabs.size > 1,
                    compact = false,
                    onSelect = { interactionListener.onSelectTab(tab.id) },
                    onClose = { interactionListener.onCloseTab(tab.id) },
                    colors = colors,
                )
            }
        }
        AslHorizontalDivider(color = colors.borderDefault, thickness = 1.dp)
    }
}

@Composable
private fun TerminalLinuxBanner(
    linux: LinuxStatus,
    onInstall: () -> Unit,
    colors: AslColorScheme,
) {
    if (!linux.supported) return
    if (linux.installed && linux.error == null) return
    Column(modifier = Modifier.background(colors.bgElevated)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AslText(
                    text = when {
                        linux.isBusy -> linux.phaseText() ?: stringResource(R.string.terminal_linux_installing)
                        linux.error != null -> stringResource(R.string.terminal_linux_install_failed)
                        else -> stringResource(R.string.terminal_linux_enable)
                    },
                    style = AslCode.codeSmall,
                    color = colors.textPrimary,
                )
                linux.error?.let {
                    AslText(text = it, style = AslCode.codeTiny, color = colors.error)
                }
            }
            if (!linux.isBusy) {
                InstallPill(
                    label = stringResource(if (linux.error != null) R.string.terminal_retry else R.string.terminal_install),
                    onClick = onInstall,
                    colors = colors,
                )
            }
        }
        if (linux.isBusy && linux.progressPercent in 1..99) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(colors.borderDefault),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(linux.progressPercent / 100f)
                        .height(2.dp)
                        .background(colors.terminalPrompt),
                )
            }
        }
        AslHorizontalDivider(color = colors.borderDefault, thickness = 1.dp)
    }
}

@Composable
internal fun LinuxStatus.phaseText(): String? = when (phase) {
    LinuxInstallPhase.DOWNLOADING -> stringResource(R.string.terminal_linux_downloading, progressPercent)
    LinuxInstallPhase.EXTRACTING -> stringResource(R.string.terminal_linux_extracting)
    LinuxInstallPhase.BOOTSTRAPPING_PACKAGES -> stringResource(R.string.terminal_linux_bootstrapping)
    null -> null
}

@Composable
private fun InstallPill(label: String, onClick: () -> Unit, colors: AslColorScheme) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .background(colors.terminalPrompt, AslShape.sm)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        AslText(text = label, style = AslCode.codeSmall, color = colors.bgBase)
    }
}

@Composable
private fun TerminalExtraKeysRow(
    interactionListener: TerminalInteractionListener,
    colors: AslColorScheme,
) {
    Column(modifier = Modifier.background(colors.bgElevated)) {
        AslHorizontalDivider(color = colors.borderDefault, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            EXTRA_KEYS.forEach { key ->
                ExtraKeyChip(label = key, onClick = { interactionListener.onExtraKeyPressed(key) })
            }
        }
    }
}

@Composable
private fun ExtraKeyChip(label: String, onClick: () -> Unit) {
    val colors = AslTheme.colors
    Box(
        modifier = Modifier
            .height(36.dp)
            .defaultMinSize(minWidth = 44.dp)
            .background(colors.surfaceContainerHigh, AslShape.sm)
            .border(1.dp, colors.borderDefault, AslShape.sm)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        AslText(text = label, style = AslCode.codeSmall, color = colors.textPrimary)
    }
}
