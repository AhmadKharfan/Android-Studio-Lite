package com.ahmadkharfan.androidstudiolite.data.local.pty

import com.ahmadkharfan.androidstudiolite.domain.model.TerminalEvent
import com.ahmadkharfan.androidstudiolite.domain.repository.TerminalRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
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

    @Volatile
    private var session: PtySession? = null
    @Volatile
    private var writer: OutputStream? = null
    private var scope: CoroutineScope? = null
    private var inputChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private var lastRows = 24
    private var lastCols = 80

    override suspend fun start(workingDirectory: String?, rows: Int, cols: Int): Unit = lifecycle.withLock {
        if (session != null) return@withLock
        inputChannel = Channel(Channel.UNLIMITED)
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
        sessionScope.launch { drainInput(launched.input) }
        sessionScope.launch { drainOutput(launched) }
    }

    private suspend fun drainInput(stream: OutputStream) {
        for (bytes in inputChannel) {
            try {
                stream.write(bytes)
                stream.flush()
            } catch (_: IOException) {

            }
        }
    }

    private suspend fun drainOutput(target: PtySession) {


        val reader = target.output.reader(StandardCharsets.UTF_8)
        val buffer = CharArray(4096)
        try {
            while (true) {
                val n = reader.read(buffer)
                if (n < 0) break
                if (n > 0) _events.emit(TerminalEvent.Bytes(String(buffer, 0, n)))
            }
        } catch (_: IOException) {

        }
        val code = runCatching { target.waitFor() }.getOrDefault(-1)
        _events.emit(TerminalEvent.CommandFinished(code))
        _events.emit(TerminalEvent.SessionEnded)
    }

    override suspend fun send(command: String) {

        writeInput(command + "\n")
    }

    override suspend fun writeInput(text: String) {
        if (text.isEmpty()) return
        if (writer == null) start()
        if (writer == null) return
        inputChannel.send(text.toByteArray(StandardCharsets.UTF_8))
    }

    override fun offerInput(text: String): Boolean {
        if (text.isEmpty()) return true
        if (writer == null) return false
        return inputChannel.trySend(text.toByteArray(StandardCharsets.UTF_8)).isSuccess
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
        inputChannel.close()
        withContext(ioDispatcher) {
            runCatching { doomed?.destroy() }
        }
        scope?.cancel()
        writer = null
        session = null
        scope = null
    }
}
