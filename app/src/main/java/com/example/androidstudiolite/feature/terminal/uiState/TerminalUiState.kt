package com.example.androidstudiolite.feature.terminal.uiState

import androidx.compose.runtime.Immutable

import com.example.androidstudiolite.core.designsystem.component.ide.AslTerminalLine

@Immutable
data class TerminalUiState(
    val sessionNumber: Int = 1,
    val lines: List<AslTerminalLine> = emptyList(),
    val input: String = "",
    val running: Boolean = false,
)
