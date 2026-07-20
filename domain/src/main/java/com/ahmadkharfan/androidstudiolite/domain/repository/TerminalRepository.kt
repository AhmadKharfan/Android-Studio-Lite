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
     * while a session is already running. [rows]/[cols] size a PTY session's initial window; the
     * line-oriented implementation ignores them.
     */
    suspend fun start(workingDirectory: String? = null, rows: Int = 24, cols: Int = 80)

    /** Writes a single command line to the shell's stdin, starting a session first if needed. */
    suspend fun send(command: String)

    /**
     * Writes raw bytes (keystrokes, escape sequences, Ctrl-C, …) to the terminal with no added newline.
     * Only meaningful for a PTY session; the line-oriented default treats it as a no-op.
     */
    suspend fun writeInput(text: String) {}

    /**
     * Non-blocking enqueue of raw PTY bytes for the keystroke hot path. Returns false if the session
     * is not running or the queue rejected the write. Default is a no-op for line-oriented repos.
     */
    fun offerInput(text: String): Boolean = false

    /**
     * Notifies the terminal it was resized to [rows]×[cols] so the child reflows (SIGWINCH). No-op for
     * the line-oriented session, which has no window.
     */
    suspend fun resize(rows: Int, cols: Int) {}

    /** Terminates the shell and releases its streams. */
    suspend fun stop()
}
