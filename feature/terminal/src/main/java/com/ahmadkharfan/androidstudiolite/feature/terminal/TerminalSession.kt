package com.ahmadkharfan.androidstudiolite.feature.terminal

import com.ahmadkharfan.androidstudiolite.domain.model.TerminalEvent
import com.ahmadkharfan.androidstudiolite.domain.repository.TerminalRepository
import com.ahmadkharfan.androidstudiolite.feature.terminal.emulator.TerminalEmulator
import com.ahmadkharfan.androidstudiolite.feature.terminal.emulator.TerminalScreen
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TerminalSession(
    val id: String,
    val title: String,
    private val repository: TerminalRepository,
    val workingDirectory: String? = null,
    initialRows: Int,
    initialCols: Int,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(ioDispatcher + SupervisorJob())
    private val emulator = TerminalEmulator(initialRows, initialCols)
    private val emulatorLock = Any()

    private val _screen = MutableStateFlow(snapshotLocked())
    val screen: StateFlow<TerminalScreen> = _screen.asStateFlow()

    private val _running = MutableStateFlow(true)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val renderTick = MutableStateFlow(0L)

    val rows: Int get() = synchronized(emulatorLock) { emulator.rows }
    val cols: Int get() = synchronized(emulatorLock) { emulator.cols }

    init {
        scope.launch { observeEvents() }
        scope.launch { renderLoop() }
        scope.launch { repository.start(workingDirectory = workingDirectory, rows = initialRows, cols = initialCols) }
    }

    private suspend fun observeEvents() {
        repository.events.collect { event ->
            when (event) {
                is TerminalEvent.Bytes -> feed(event.text)

                is TerminalEvent.Output -> feed(event.line.text + "\r\n")
                is TerminalEvent.CommandFinished -> Unit
                TerminalEvent.SessionEnded -> _running.value = false
            }
        }
    }

    private fun feed(text: String) {
        synchronized(emulatorLock) { emulator.feed(text) }
        renderTick.value += 1
    }

    private suspend fun renderLoop() {


        var lastTick = renderTick.value
        while (true) {
            val tick = renderTick.value
            if (tick != lastTick) {
                lastTick = tick
                _screen.value = synchronized(emulatorLock) { emulator.snapshot() }
            }
            delay(FRAME_INTERVAL_MS)
        }
    }

    fun offerInput(text: String): Boolean = repository.offerInput(text)

    fun submitInput(text: String) {
        scope.launch { repository.writeInput(text) }
    }

    suspend fun writeInput(text: String) = repository.writeInput(text)

    fun resize(newRows: Int, newCols: Int) {
        val changed = synchronized(emulatorLock) {
            if (emulator.rows == newRows && emulator.cols == newCols) {
                false
            } else {
                emulator.resize(newRows, newCols)
                true
            }
        }
        if (changed) {
            renderTick.value += 1
            scope.launch { runCatching { repository.resize(newRows, newCols) } }
        }
    }

    fun destroy() {
        scope.launch { runCatching { repository.stop() } }
            .invokeOnCompletion { scope.cancel() }
    }

    private fun snapshotLocked(): TerminalScreen = synchronized(emulatorLock) { emulator.snapshot() }

    private companion object {
        const val FRAME_INTERVAL_MS = 16L
    }
}
