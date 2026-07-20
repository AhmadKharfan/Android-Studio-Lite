package com.ahmadkharfan.androidstudiolite.feature.terminal

import androidx.lifecycle.viewModelScope
import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.core.linux.LinuxBootstrapInstaller
import com.ahmadkharfan.androidstudiolite.core.linux.LinuxInstallState
import com.ahmadkharfan.androidstudiolite.core.linux.ProotEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private const val DEFAULT_ROWS = 24
private const val DEFAULT_COLS = 80

@OptIn(ExperimentalCoroutinesApi::class)
class TerminalViewModel(
    private val sessionManager: TerminalSessionManager,
    private val linuxInstaller: LinuxBootstrapInstaller,
    private val proot: ProotEnvironment,
) : BaseViewModel<TerminalUiState, Nothing>(
    initialState = TerminalUiState(),
), TerminalInteractionListener {

    private var measuredRows = DEFAULT_ROWS
    private var measuredCols = DEFAULT_COLS

    init {
        linuxInstaller.refreshState()
        viewModelScope.launch { linuxInstaller.ensureBootstrapPackages() }
        sessionManager.ensureSession(measuredRows, measuredCols)
        viewModelScope.launch {
            derivedState().collect { newState ->
                updateState { copy(linux = newState.linux, tabs = newState.tabs, activeTabId = newState.activeTabId, screen = newState.screen) }
            }
        }
    }

    private fun derivedState(): Flow<TerminalUiState> {
        val tabsState = combine(sessionManager.sessions, sessionManager.activeId) { sessions, activeId ->
            sessions to activeId
        }.flatMapLatest { (sessions, activeId) ->
            if (sessions.isEmpty()) {
                flowOf(TerminalUiState())
            } else {
                val active = sessions.firstOrNull { it.id == activeId } ?: sessions.first()
                val runningFlows = sessions.map { it.running }
                combine(active.screen, combine(runningFlows) { it.toList() }) { screen, runnings ->
                    TerminalUiState(
                        tabs = sessions.mapIndexed { i, s -> TerminalTab(s.id, s.title, runnings[i]) },
                        activeTabId = active.id,
                        screen = screen,
                    )
                }
            }
        }
        return combine(tabsState, linuxInstaller.state) { base, install ->
            base.copy(linux = install.toLinuxStatus(proot))
        }
    }

    override fun onKeyInput(text: String) {
        if (text.isEmpty()) return
        val session = sessionManager.activeSession() ?: return
        if (!session.offerInput(text)) session.submitInput(text)
    }

    override fun onSpecialKey(key: TerminalKey) {
        val bytes = TerminalKeys.bytes(key)
        val session = sessionManager.activeSession() ?: return
        if (!session.offerInput(bytes)) session.submitInput(bytes)
    }

    override fun onResize(rows: Int, cols: Int) {
        measuredRows = rows.coerceAtLeast(1)
        measuredCols = cols.coerceAtLeast(1)
        sessionManager.activeSession()?.resize(measuredRows, measuredCols)
    }

    override fun onExtraKeyPressed(key: String) {
        TerminalKeys.specialForExtraKey(key)?.let { onSpecialKey(it); return }
        TerminalKeys.textForExtraKey(key)?.let { onKeyInput(it) }
    }

    override fun onNewSession() {
        sessionManager.newSession(measuredRows, measuredCols)
    }

    override fun onSelectTab(id: String) {
        sessionManager.select(id)

        sessionManager.session(id)?.resize(measuredRows, measuredCols)
    }

    override fun onCloseTab(id: String) {
        sessionManager.close(id)

        sessionManager.ensureSession(measuredRows, measuredCols)
    }

    override fun onInstallLinux() {
        viewModelScope.launch {
            linuxInstaller.install()
            linuxInstaller.refreshState()
            if (linuxInstaller.state.value is LinuxInstallState.Installed || proot.isInstalled()) {
                sessionManager.restartAllSessions(measuredRows, measuredCols)
            }
        }
    }

    override fun onOpenSettings() {
        updateState { copy(settingsVisible = true) }
    }

    override fun onDismissSettings() {
        updateState { copy(settingsVisible = false) }
    }

    override fun onReinstallLinux() {
        viewModelScope.launch {
            linuxInstaller.uninstall()
            linuxInstaller.install()
            linuxInstaller.refreshState()
            if (linuxInstaller.state.value is LinuxInstallState.Installed || proot.isInstalled()) {
                sessionManager.restartAllSessions(measuredRows, measuredCols)
            }
        }
    }
}
