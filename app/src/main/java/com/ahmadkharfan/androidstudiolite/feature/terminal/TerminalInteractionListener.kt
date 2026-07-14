package com.ahmadkharfan.androidstudiolite.feature.terminal

interface TerminalInteractionListener {
    /** Raw text/keystrokes typed by the user; written straight to the PTY (no added newline). */
    fun onKeyInput(text: String)

    /** A non-printing key (Enter, Backspace, arrows, Tab, Esc, Ctrl-combos) → its terminal byte sequence. */
    fun onSpecialKey(key: TerminalKey)

    /** The terminal view measured a new character grid size; resize the PTY (SIGWINCH) to match. */
    fun onResize(rows: Int, cols: Int)

    /** One of the on-screen helper keys was tapped (Esc, Ctrl, Tab, arrows, …). */
    fun onExtraKeyPressed(key: String)

    /** Kill the current shell and start a fresh session. */
    fun onNewSession()
}

/** Non-printing keys the terminal translates into control/escape byte sequences for the PTY. */
enum class TerminalKey {
    Enter, Backspace, Tab, Escape, ArrowUp, ArrowDown, ArrowLeft, ArrowRight, CtrlC, CtrlD, CtrlL, CtrlZ,
}
