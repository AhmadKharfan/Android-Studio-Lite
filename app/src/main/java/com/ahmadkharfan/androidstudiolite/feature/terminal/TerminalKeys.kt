package com.ahmadkharfan.androidstudiolite.feature.terminal

/**
 * Translates non-printing keys and the on-screen helper keys into the exact byte sequences a PTY
 * expects. Pure and side-effect-free so the mapping is unit-tested directly.
 */
object TerminalKeys {

    /** The bytes to write to the PTY for a [TerminalKey]. */
    fun bytes(key: TerminalKey): String = when (key) {
        TerminalKey.Enter -> "\r"
        TerminalKey.Backspace -> "\u007F" // DEL — what xterm sends for Backspace by default
        TerminalKey.Tab -> "\t"
        TerminalKey.Escape -> "\u001B"
        TerminalKey.ArrowUp -> "\u001B[A"
        TerminalKey.ArrowDown -> "\u001B[B"
        TerminalKey.ArrowRight -> "\u001B[C"
        TerminalKey.ArrowLeft -> "\u001B[D"
        TerminalKey.CtrlC -> "\u0003"
        TerminalKey.CtrlD -> "\u0004"
        TerminalKey.CtrlL -> "\u000C"
        TerminalKey.CtrlZ -> "\u001A"
    }

    /** Map an on-screen helper-key label to the [TerminalKey] it stands for, or null if it inserts text. */
    fun specialForExtraKey(label: String): TerminalKey? = when (label) {
        "Esc" -> TerminalKey.Escape
        "Tab" -> TerminalKey.Tab
        "←" -> TerminalKey.ArrowLeft
        "↑" -> TerminalKey.ArrowUp
        "↓" -> TerminalKey.ArrowDown
        "→" -> TerminalKey.ArrowRight
        "Ctrl+C" -> TerminalKey.CtrlC
        else -> null
    }

    /** Text an on-screen helper key inserts literally (e.g. `/`, `|`), or null if it's a special key. */
    fun textForExtraKey(label: String): String? = when (label) {
        "/", "|", "~", "-" -> label
        else -> null
    }
}
