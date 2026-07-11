package com.example.androidstudiolite.feature.terminal

interface TerminalInteractionListener {
    fun onInputChanged(value: String)
    fun onSubmitCommand()
    fun onNewSession()
    fun onExtraKeyPressed(key: String)
}
