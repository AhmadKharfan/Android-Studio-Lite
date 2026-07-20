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

class ShellTerminalRepository(
    private val shellPathProvider: () -> String,
    private val environmentProvider: () -> Map<String, String>,
    private val defaultWorkingDirectory: () -> File?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TerminalRepository {

    private val _events = MutableSharedFlow<TerminalEvent>(extraBufferCapacity = 256)
    override val events: SharedFlow<TerminalEvent> = _events.asSharedFlow()

    private val lifecycle = Mutex()
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var scope: CoroutineScope? = null

    override suspend fun start(workingDirectory: String?, rows: Int, cols: Int): Unit = lifecycle.withLock {
        if (process != null) return@withLock
        val dir = workingDirectory?.let(::File) ?: defaultWorkingDirectory()
        val sessionScope = CoroutineScope(ioDispatcher + SupervisorJob())
        val launched = withContext(ioDispatcher) {


            ProcessBuilder(shellPathProvider())
                .directory(dir?.takeIf { it.exists() })
                .redirectErrorStream(true)
                .apply {

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

                    if (sentinelAt > 0) emitOutput(line.substring(0, sentinelAt))
                    val exit = line.substring(sentinelAt + SENTINEL.length).trim().toIntOrNull() ?: 0
                    _events.emit(TerminalEvent.CommandFinished(exit))
                } else {
                    emitOutput(line)
                }
            }
        } catch (_: IOException) {

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

                target.appendLine("echo \"$SENTINEL\$?\"")
                target.flush()
            } catch (_: IOException) {

            }
        }
    }

    override suspend fun stop(): Unit = lifecycle.withLock {
        val doomed = process
        withContext(ioDispatcher) {
            try {
                writer?.close()
            } catch (_: IOException) {

            }
            doomed?.destroy()
        }
        scope?.cancel()
        writer = null
        process = null
        scope = null
    }

    private companion object {
        const val SENTINEL = "__ASL_CMD_DONE_9f3c1a__"
    }
}
