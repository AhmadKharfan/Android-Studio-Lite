package com.ahmadkharfan.androidstudiolite.feature.terminal

object TerminalKeys {

    fun bytes(key: TerminalKey): String = when (key) {
        TerminalKey.Enter -> "\r"
        TerminalKey.Backspace -> "\u007F"
        TerminalKey.Delete -> "\u001B[3~"
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

    fun textForExtraKey(label: String): String? = when (label) {
        "/", "|", "~", "-" -> label
        else -> null
    }
}
