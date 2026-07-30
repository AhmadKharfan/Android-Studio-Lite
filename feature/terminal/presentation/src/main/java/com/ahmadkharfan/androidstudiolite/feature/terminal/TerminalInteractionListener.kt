package com.ahmadkharfan.androidstudiolite.feature.terminal

interface TerminalInteractionListener {
    fun onKeyInput(text: String)

    fun onSpecialKey(key: TerminalKey)

    fun onResize(rows: Int, cols: Int)

    fun onExtraKeyPressed(key: String)

    fun onNewSession()

    fun onSelectTab(id: String)

    fun onCloseTab(id: String)

    fun onInstallLinux()

    fun onOpenSettings()

    fun onDismissSettings()

    fun onReinstallLinux()
}

enum class TerminalKey {
    Enter, Backspace, Delete, Tab, Escape, ArrowUp, ArrowDown, ArrowLeft, ArrowRight, CtrlC, CtrlD, CtrlL, CtrlZ,
}
