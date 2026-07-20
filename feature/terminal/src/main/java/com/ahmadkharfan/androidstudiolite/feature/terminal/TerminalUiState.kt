package com.ahmadkharfan.androidstudiolite.feature.terminal

import androidx.compose.runtime.Immutable
import com.ahmadkharfan.androidstudiolite.feature.terminal.emulator.TerminalScreen

@Immutable
data class TerminalUiState(
    /** Open terminal tabs, in display order. */
    val tabs: List<TerminalTab> = emptyList(),
    /** Id of the tab currently shown, or null before the first tab is created. */
    val activeTabId: String? = null,
    /** The emulated screen of the active tab, updated as PTY output arrives (frame-throttled). */
    val screen: TerminalScreen = TerminalScreen.blank(24, 80),
    /** State of the optional downloadable Linux userland (proot). */
    val linux: LinuxStatus = LinuxStatus(),
    /** True while the terminal settings sheet is open. */
    val settingsVisible: Boolean = false,
)

/** UI-facing status of the Linux userland install (see LinuxBootstrapInstaller). */
@Immutable
data class LinuxStatus(
    /** False when the device architecture has no published rootfs. */
    val supported: Boolean = true,
    /** True once the Alpine rootfs is installed and new tabs launch under proot. */
    val installed: Boolean = false,
    /** True while downloading or extracting. */
    val busy: Boolean = false,
    /** Download progress 0..100 (only meaningful while [busy]). */
    val progressPercent: Int = 0,
    /** Human-readable phase label while [busy], e.g. "Downloading 42%". */
    val phase: String? = null,
    /** Last failure message, if any. */
    val error: String? = null,
)

@Immutable
data class TerminalTab(
    val id: String,
    val title: String,
    /** False once the tab's shell has exited; the tab stays open showing its final screen. */
    val running: Boolean,
)
