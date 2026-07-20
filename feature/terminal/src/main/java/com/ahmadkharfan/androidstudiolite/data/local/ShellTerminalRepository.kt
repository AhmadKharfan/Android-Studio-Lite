package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.TerminalEvent
import com.ahmadkharfan.androidstudiolite.domain.model.TerminalLineKind
import com.ahmadkharfan.androidstudiolite.domain.model.TerminalOutputLine
import com.ahmadkharfan.androidstudiolite.domain.repository.TerminalRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.IOException

/**
 * Real, ProcessBuilder-based shell session — ASL's own implementation (no GPL terminal-emulator code
 * was consulted or copied). It launches one long-lived `sh` reading commands from its stdin, injects
 * the IDE toolchain environment, and streams merged stdout/stderr back out as [TerminalEvent]s.
 *
 * ### Command boundaries without a PTY
 * A pipe-fed `sh` prints no prompt, so there is no natural "command done" signal. After every user
 * command we write a second line — `echo "<sentinel>$?"` — and the reader treats a line containing
 * [SENTINEL] as the boundary: it is swallowed (never shown) and turned into
 * [TerminalEvent.CommandFinished] carrying the preceding command's exit code. The trade-off is the
 * documented [TerminalRepository] limitation: a command that itself consumes stdin (e.g. `cat`) will
 * swallow the sentinel, so interactive programs are unsupported until the Phase 6 PTY lands.
 *
 * Dependencies are passed as providers so the session re-reads them on each [start] — the toolchain
 * shell at `$PREFIX/bin/sh` may only appear after the user installs it. The class holds no Android
 * types, which keeps it a plain JVM unit under test.
 */
class ShellTerminalRepository(
    private val shellPathProvider: () -> String,
    private val environmentProvider: () -> Map<String, String>,
    private val defaultWorkingDirectory: () -> File?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TerminalRepository {

    private val _events = MutableSharedFlow<TerminalEvent>(extraBufferCapacity = 256)
    override val events: SharedFlow<TerminalEvent> = _events.asSharedFlow()

    /** Guards [process]/[writer]/[scope] against concurrent start/send/stop. */
    private val lifecycle = Mutex()
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var scope: CoroutineScope? = null

    override suspend fun start(workingDirectory: String?, rows: Int, cols: Int): Unit = lifecycle.withLock {
        if (process != null) return@withLock
        val dir = workingDirectory?.let(::File) ?: defaultWorkingDirectory()
        val sessionScope = CoroutineScope(ioDispatcher + SupervisorJob())
        val launched = withContext(ioDispatcher) {
            // Plain (non-interactive) sh reading commands from the piped stdin: no prompt, no
            // job-control warnings, and cd/exports persist across sends for the session's lifetime.
            ProcessBuilder(shellPathProvider())
                .directory(dir?.takeIf { it.exists() })
                .redirectErrorStream(true)
                .apply {
                    // Start from the toolchain environment; ProcessBuilder seeds a minimal PATH itself.
                    environment().putAll(environmentProvider())
                }
                .start()
        }
        process = launched
        writer = launched.outputStream.bufferedWriter()
        scope = sessionScope
        sessionScope.launch { drainOutput(launched) }
    }

    private suspend fun drainOutput(target: Process) {
        val reader = target.inputStream.bufferedReader()
        try {
            while (true) {
                val line = reader.readLine() ?: break
                val sentinelAt = line.indexOf(SENTINEL)
                if (sentinelAt >= 0) {
                    // Any real output printed on the same line as the sentinel still counts.
                    if (sentinelAt > 0) emitOutput(line.substring(0, sentinelAt))
                    val exit = line.substring(sentinelAt + SENTINEL.length).trim().toIntOrNull() ?: 0
                    _events.emit(TerminalEvent.CommandFinished(exit))
                } else {
                    emitOutput(line)
                }
            }
        } catch (_: IOException) {
            // Stream closed underneath us (stop() destroyed the process) — fall through to teardown.
        }
        _events.emit(TerminalEvent.SessionEnded)
    }

    private suspend fun emitOutput(text: String) {
        _events.emit(TerminalEvent.Output(TerminalOutputLine(text, TerminalLineKind.Stdout)))
    }

    override suspend fun send(command: String) {
        start()
        val target = writer ?: return
        withContext(ioDispatcher) {
            try {
                target.appendLine(command)
                // Print the exit status of the command above, tagged so the reader can bracket it.
                target.appendLine("echo \"$SENTINEL\$?\"")
                target.flush()
            } catch (_: IOException) {
                // The shell died between checks; a SessionEnded event will already be in flight.
            }
        }
    }

    override suspend fun stop(): Unit = lifecycle.withLock {
        val doomed = process
        withContext(ioDispatcher) {
            try {
                writer?.close()
            } catch (_: IOException) {
                // Already closed; nothing to flush.
            }
            doomed?.destroy()
        }
        scope?.cancel()
        writer = null
        process = null
        scope = null
    }

    private companion object {
        /** Unlikely-to-collide marker prefixing the exit code that ends each command's output. */
        const val SENTINEL = "__ASL_CMD_DONE_9f3c1a__"
    }
}
