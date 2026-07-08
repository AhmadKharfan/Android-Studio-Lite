package com.example.androidstudiolite.feature.terminal.interaction

sealed interface TerminalInteraction {
    data class InputChanged(val value: String) : TerminalInteraction
    data object SubmitCommand : TerminalInteraction
    data object NewSession : TerminalInteraction
    data class ExtraKeyPressed(val key: String) : TerminalInteraction
}
