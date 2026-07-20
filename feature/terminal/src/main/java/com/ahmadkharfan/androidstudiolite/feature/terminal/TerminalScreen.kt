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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslIconButton
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

// On-screen helper keys for things a soft keyboard makes awkward (Esc, arrows, Ctrl-C, pipes).
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
        TerminalSettingsSheet(
            linux = uiState.linux,
            onDismiss = { viewModel.onDismissSettings() },
            onInstallLinux = {
                viewModel.onDismissSettings()
                viewModel.onInstallLinux()
            },
            onReinstallLinux = {
                viewModel.onDismissSettings()
                viewModel.onReinstallLinux()
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
    Scaffold(containerColor = colors.bgBase) { padding ->
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
            AslIconButton(icon = "arrow-left", contentDescription = "Back", onClick = onBack)
            Text(
                text = "Terminal",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            AslIconButton(icon = "plus", contentDescription = "New session", onClick = { interactionListener.onNewSession() })
            AslIconButton(icon = "settings-2", contentDescription = "Terminal settings", onClick = { interactionListener.onOpenSettings() })
        }
        HorizontalDivider(color = colors.borderDefault, thickness = 1.dp)
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
                    onSelect = { interactionListener.onSelectTab(tab.id) },
                    onClose = { interactionListener.onCloseTab(tab.id) },
                    colors = colors,
                )
            }
        }
        HorizontalDivider(color = colors.borderDefault, thickness = 1.dp)
    }
}

@Composable
private fun TerminalTabChip(
    tab: TerminalTab,
    active: Boolean,
    canClose: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    colors: AslColorScheme,
) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .background(if (active) colors.surfaceContainerHigh else colors.bgElevated, AslShape.sm)
            .border(1.dp, if (active) colors.borderStrong else colors.borderDefault, AslShape.sm)
            .clickable(onClick = onSelect)
            .padding(start = 10.dp, end = if (canClose) 4.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (tab.running) tab.title else "${tab.title} (exited)",
            style = AslCode.codeSmall,
            color = if (active) colors.textPrimary else colors.textSecondary,
        )
        if (canClose) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "×", style = AslCode.codeSmall, color = colors.textTertiary)
            }
        }
    }
}

@Composable
private fun TerminalLinuxBanner(
    linux: LinuxStatus,
    onInstall: () -> Unit,
    colors: AslColorScheme,
) {
    // Nothing to offer once installed, or on an architecture with no rootfs.
    if (linux.installed || !linux.supported) return
    Column(modifier = Modifier.background(colors.bgElevated)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (linux.busy) (linux.phase ?: "Installing Linux…")
                    else "Enable full Linux — apk, python, git, gcc and more",
                    style = AslCode.codeSmall,
                    color = colors.textPrimary,
                )
                linux.error?.let {
                    Text(text = "Failed: $it", style = AslCode.codeTiny, color = colors.textSecondary)
                }
            }
            if (!linux.busy) {
                InstallPill(label = if (linux.error != null) "Retry" else "Install", onClick = onInstall, colors = colors)
            }
        }
        if (linux.busy && linux.progressPercent in 1..99) {
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
        HorizontalDivider(color = colors.borderDefault, thickness = 1.dp)
    }
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
        Text(text = label, style = AslCode.codeSmall, color = colors.bgBase)
    }
}

@Composable
private fun TerminalExtraKeysRow(
    interactionListener: TerminalInteractionListener,
    colors: AslColorScheme,
) {
    Column(modifier = Modifier.background(colors.bgElevated)) {
        HorizontalDivider(color = colors.borderDefault, thickness = 1.dp)
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
        Text(text = label, style = AslCode.codeSmall, color = colors.textPrimary)
    }
}
