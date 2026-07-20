package com.ahmadkharfan.androidstudiolite.domain.model

sealed interface TerminalEvent {

    data class Output(val line: TerminalOutputLine) : TerminalEvent

    data class Bytes(val text: String) : TerminalEvent

    data class CommandFinished(val exitCode: Int) : TerminalEvent

    data object SessionEnded : TerminalEvent
}
