package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.TerminalEvent
import kotlinx.coroutines.flow.Flow

/**
 * A long-lived, line-oriented shell session. [events] is a hot stream: subscribe **before** calling
 * [start] to avoid missing the first lines. State (working directory, exported vars) persists across
 * [send] calls for the life of the session, exactly like typing into an interactive shell — but
 * without a PTY, so interactive/curses programs are not supported (see [TerminalEvent]).
 */
interface TerminalRepository {

    /** Merged stdout/stderr lines plus lifecycle events for the current session. */
    val events: Flow<TerminalEvent>

    /**
     * Launches the shell rooted at [workingDirectory] (defaults to the IDE home). Idempotent: a no-op
     * while a session is already running.
     */
    suspend fun start(workingDirectory: String? = null)

    /** Writes a single command line to the shell's stdin, starting a session first if needed. */
    suspend fun send(command: String)

    /** Terminates the shell and releases its streams. */
    suspend fun stop()
}
