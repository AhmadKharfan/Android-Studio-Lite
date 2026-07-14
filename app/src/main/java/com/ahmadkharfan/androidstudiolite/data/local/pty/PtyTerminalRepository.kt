package com.ahmadkharfan.androidstudiolite.data.local.pty

import com.ahmadkharfan.androidstudiolite.domain.model.TerminalEvent
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
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * A full pseudo-terminal session (T12), the PTY successor to [ShellTerminalRepository][
 * com.ahmadkharfan.androidstudiolite.data.local.ShellTerminalRepository]. It forks a real shell behind
 * a master PTY so interactive and curses programs work, streams the child's raw output as
 * [TerminalEvent.Bytes] (control sequences intact, for a terminal emulator to interpret), forwards
 * keystrokes verbatim via [writeInput], and propagates window resizes ([resize] → SIGWINCH).
 *
 * The PTY itself is obtained through a [PtySessionFactory] so the read loop and plumbing are unit
 * testable without the native library. Holds no Android types.
 */
class PtyTerminalRepository(
    private val shellCommandProvider: () -> List<String>,
    private val environmentProvider: () -> Map<String, String>,
    private val defaultWorkingDirectory: () -> File?,
    private val sessionFactory: PtySessionFactory = RealPtySessionFactory,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TerminalRepository {

    private val _events = MutableSharedFlow<TerminalEvent>(extraBufferCapacity = 256)
    override val events: SharedFlow<TerminalEvent> = _events.asSharedFlow()

    private val lifecycle = Mutex()
    private var session: PtySession? = null
    private var writer: OutputStream? = null
    private var scope: CoroutineScope? = null
    private var lastRows = 24
    private var lastCols = 80

    override suspend fun start(workingDirectory: String?, rows: Int, cols: Int): Unit = lifecycle.withLock {
        if (session != null) return@withLock
        val dir = workingDirectory?.let(::File) ?: defaultWorkingDirectory()
        lastRows = rows.coerceAtLeast(1)
        lastCols = cols.coerceAtLeast(1)
        val sessionScope = CoroutineScope(ioDispatcher + SupervisorJob())
        val launched = withContext(ioDispatcher) {
            sessionFactory.open(
                command = shellCommandProvider(),
                environment = environmentProvider(),
                workingDir = dir?.takeIf { it.exists() },
                rows = lastRows,
                cols = lastCols,
            )
        }
        session = launched
        writer = launched.input
        scope = sessionScope
        sessionScope.launch { drainOutput(launched) }
    }

    private suspend fun drainOutput(target: PtySession) {
        // Decode straight from the byte stream as UTF-8 so multi-byte glyphs and box-drawing survive;
        // the emulator downstream works on the decoded characters.
        val reader = target.output.reader(StandardCharsets.UTF_8)
        val buffer = CharArray(4096)
        try {
            while (true) {
                val n = reader.read(buffer)
                if (n < 0) break
                if (n > 0) _events.emit(TerminalEvent.Bytes(String(buffer, 0, n)))
            }
        } catch (_: IOException) {
            // Master closed underneath us (stop() destroyed the session) — fall through to teardown.
        }
        val code = runCatching { target.waitFor() }.getOrDefault(-1)
        _events.emit(TerminalEvent.CommandFinished(code))
        _events.emit(TerminalEvent.SessionEnded)
    }

    override suspend fun send(command: String) {
        // A PTY has no command framing; "send a command" is just typing it followed by Enter.
        writeInput(command + "\n")
    }

    override suspend fun writeInput(text: String) {
        start()
        val target = writer ?: return
        withContext(ioDispatcher) {
            try {
                target.write(text.toByteArray(StandardCharsets.UTF_8))
                target.flush()
            } catch (_: IOException) {
                // The shell died between checks; a SessionEnded event will already be in flight.
            }
        }
    }

    override suspend fun resize(rows: Int, cols: Int) {
        lastRows = rows.coerceAtLeast(1)
        lastCols = cols.coerceAtLeast(1)
        val target = session ?: return
        withContext(ioDispatcher) {
            runCatching { target.resize(lastRows, lastCols) }
        }
    }

    override suspend fun stop(): Unit = lifecycle.withLock {
        val doomed = session
        withContext(ioDispatcher) {
            runCatching { doomed?.destroy() }
        }
        scope?.cancel()
        writer = null
        session = null
        scope = null
    }
}
