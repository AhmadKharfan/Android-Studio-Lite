package com.ahmadkharfan.androidstudiolite.feature.terminal

import androidx.compose.runtime.Immutable
import com.ahmadkharfan.androidstudiolite.feature.terminal.emulator.TerminalScreen

@Immutable
data class TerminalUiState(
    val tabs: List<TerminalTab> = emptyList(),
    val activeTabId: String? = null,
    val screen: TerminalScreen = TerminalScreen.blank(24, 80),
    val linux: LinuxStatus = LinuxStatus(),
    val settingsVisible: Boolean = false,
)

@Immutable
data class LinuxStatus(
    val supported: Boolean = true,
    val installed: Boolean = false,
    val isBusy: Boolean = false,
    val progressPercent: Int = 0,
    val phase: String? = null,
    val error: String? = null,
)

@Immutable
data class TerminalTab(
    val id: String,
    val title: String,
    val running: Boolean,
)
