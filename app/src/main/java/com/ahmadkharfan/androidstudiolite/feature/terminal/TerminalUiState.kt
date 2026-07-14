package com.ahmadkharfan.androidstudiolite.feature.terminal

import androidx.compose.runtime.Immutable
import com.ahmadkharfan.androidstudiolite.feature.terminal.emulator.TerminalScreen

@Immutable
data class TerminalUiState(
    val sessionNumber: Int = 1,
    /** The emulated screen grid rendered by the terminal view; updated as PTY output arrives. */
    val screen: TerminalScreen = TerminalScreen.blank(24, 80),
    val running: Boolean = true,
)
