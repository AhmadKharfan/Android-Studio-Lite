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

    /** Open a new terminal tab (existing tabs keep running) and make it active. */
    fun onNewSession()

    /** Switch to the tab with [id] without disturbing any running shell. */
    fun onSelectTab(id: String)

    /** Close the tab with [id], terminating only that tab's shell. */
    fun onCloseTab(id: String)

    /** Download + install the full Linux userland; on success a fresh Linux tab is opened. */
    fun onInstallLinux()

    /** Open the terminal settings sheet. */
    fun onOpenSettings()

    /** Close the terminal settings sheet. */
    fun onDismissSettings()

    /** Remove and re-download the Linux userland. */
    fun onReinstallLinux()
}

/** Non-printing keys the terminal translates into control/escape byte sequences for the PTY. */
enum class TerminalKey {
    Enter, Backspace, Delete, Tab, Escape, ArrowUp, ArrowDown, ArrowLeft, ArrowRight, CtrlC, CtrlD, CtrlL, CtrlZ,
}
