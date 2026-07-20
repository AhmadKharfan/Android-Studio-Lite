package com.ahmadkharfan.androidstudiolite.feature.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmadkharfan.androidstudiolite.core.linux.LinuxBootstrapInstaller
import com.ahmadkharfan.androidstudiolite.core.linux.LinuxInstallState
import com.ahmadkharfan.androidstudiolite.core.linux.ProotEnvironment
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val PANEL_ROWS = 16
private const val PANEL_COLS = 80

/**
 * Compact terminal for the editor bottom panel: multi-tab, project cwd, scroll + long-press copy.
 */
@Composable
fun EditorEmbeddedTerminal(
    projectRootPath: String,
    modifier: Modifier = Modifier,
    sessionManager: TerminalSessionManager = koinInject(),
    linuxInstaller: LinuxBootstrapInstaller = koinInject(),
    proot: ProotEnvironment = koinInject(),
) {
    val colors = AslTheme.colors
    val scope = rememberCoroutineScope()
    var installedOnDisk by remember { mutableStateOf(proot.isInstalled()) }

    LaunchedEffect(projectRootPath) {
        linuxInstaller.refreshState()
        linuxInstaller.ensureBootstrapPackages()
        installedOnDisk = proot.isInstalled()
        if (projectRootPath.isNotBlank()) {
            sessionManager.ensureProjectTerminal(projectRootPath, PANEL_ROWS, PANEL_COLS)
        }
    }

    val linuxState by linuxInstaller.state.collectAsStateWithLifecycle()
    val linux = linuxState.toLinuxStatus(proot).copy(installed = installedOnDisk || linuxState is LinuxInstallState.Installed)

    val sessions by sessionManager.sessions.collectAsStateWithLifecycle()
    val projectActiveIds by sessionManager.projectActiveIds.collectAsStateWithLifecycle()
    val projectKey = remember(projectRootPath) { TerminalSessionManager.projectKey(projectRootPath) }
    val projectTabs = remember(sessions, projectRootPath) { sessionManager.projectTabs(projectRootPath) }
    val activeProjectTabId = projectActiveIds[projectKey] ?: projectTabs.firstOrNull()?.id

    Column(modifier = modifier.fillMaxSize().background(colors.bgBase)) {
        EmbeddedLinuxBanner(
            linux = linux,
            onInstall = {
                scope.launch {
                    linuxInstaller.install()
                    linuxInstaller.refreshState()
                    installedOnDisk = proot.isInstalled()
                    if (installedOnDisk && projectRootPath.isNotBlank()) {
                        sessionManager.restartAllSessions(PANEL_ROWS, PANEL_COLS)
                        sessionManager.ensureProjectTerminal(projectRootPath, PANEL_ROWS, PANEL_COLS)
                    }
                }
            },
        )
        if (projectRootPath.isNotBlank() && projectTabs.isNotEmpty()) {
            TerminalTabRow(
                tabs = projectTabs.map { TerminalTab(it.id, it.title, running = true) },
                activeTabId = activeProjectTabId,
                onSelect = { sessionManager.selectProjectTab(projectRootPath, it) },
                onClose = { sessionManager.closeProjectTab(projectRootPath, it) },
                onNewTab = { sessionManager.newProjectTab(projectRootPath, PANEL_ROWS, PANEL_COLS) },
                compact = true,
            )
            activeProjectTabId?.let { activeId ->
                EmbeddedTerminalViewport(
                    sessionId = activeId,
                    sessionManager = sessionManager,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun EmbeddedTerminalViewport(
    sessionId: String,
    sessionManager: TerminalSessionManager,
    modifier: Modifier = Modifier,
) {
    val colors = AslTheme.colors
    var measuredRows by remember { mutableIntStateOf(PANEL_ROWS) }
    var measuredCols by remember { mutableIntStateOf(PANEL_COLS) }

    val sessions by sessionManager.sessions.collectAsStateWithLifecycle()
    val active = sessions.firstOrNull { it.id == sessionId } ?: return
    val screen by active.screen.collectAsStateWithLifecycle()

    TerminalEmulatorView(
        screen = screen,
        background = colors.terminalBg,
        foreground = colors.terminalStdout,
        cursorColor = colors.terminalPrompt,
        onKey = { text ->
            if (!active.offerInput(text)) active.submitInput(text)
        },
        onSpecialKey = { key ->
            val bytes = TerminalKeys.bytes(key)
            if (!active.offerInput(bytes)) active.submitInput(bytes)
        },
        onResize = { rows, cols ->
            measuredRows = rows.coerceAtLeast(1)
            measuredCols = cols.coerceAtLeast(1)
            active.resize(measuredRows, measuredCols)
        },
        enableVolumeKeys = true,
        requestKeyboardOnAttach = false,
        modifier = modifier,
    )
}

@Composable
private fun EmbeddedLinuxBanner(
    linux: LinuxStatus,
    onInstall: () -> Unit,
) {
    if (linux.installed || !linux.supported) return
    val colors = AslTheme.colors
    Column(modifier = Modifier.background(colors.bgElevated)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (linux.busy) (linux.phase ?: "Installing Linux…")
                else "Install Linux for apk, git, and more",
                style = AslCode.codeTiny,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            if (!linux.busy) {
                Text(
                    text = if (linux.error != null) "Retry" else "Install",
                    style = AslCode.codeSmall,
                    color = colors.terminalPrompt,
                    modifier = Modifier
                        .background(colors.surfaceContainerHigh, AslShape.sm)
                        .clickable(onClick = onInstall)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        HorizontalDivider(color = colors.borderDefault, thickness = 1.dp)
    }
}
