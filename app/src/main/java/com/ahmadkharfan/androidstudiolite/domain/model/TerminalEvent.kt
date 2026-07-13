package com.ahmadkharfan.androidstudiolite.domain.model

/**
 * A single thing that happens on a live shell session, streamed out of [TerminalRepository][
 * com.ahmadkharfan.androidstudiolite.domain.repository.TerminalRepository].
 *
 * The session is **line-oriented**, not a full PTY: output arrives one line at a time and each
 * submitted command is bracketed by a [CommandFinished] carrying its exit status. There is no
 * terminal emulation, so interactive/curses programs (vim, top, `ssh` password prompts, progress
 * bars that rewrite a line) will not render correctly. A real PTY is deferred to Phase 6.
 */
sealed interface TerminalEvent {

    /** One line of merged stdout/stderr produced by the shell. */
    data class Output(val line: TerminalOutputLine) : TerminalEvent

    /** Emitted once the command most recently sent has completed, carrying its shell exit code. */
    data class CommandFinished(val exitCode: Int) : TerminalEvent

    /** The shell process itself has ended (EOF / killed); no further output will follow until restart. */
    data object SessionEnded : TerminalEvent
}
