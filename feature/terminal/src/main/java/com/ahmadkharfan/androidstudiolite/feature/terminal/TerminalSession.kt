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

/**
 * One live terminal tab: a [TerminalRepository] (its own PTY) driving a private [TerminalEmulator],
 * exposed to the UI as a conflated [screen] snapshot flow.
 *
 * ### Why the render loop
 * A PTY can emit output far faster than the screen can redraw. Feeding the emulator per chunk and
 * pushing a new snapshot on every chunk causes a recomposition/GC storm that shows up as input lag
 * and scroll jank. Instead the emulator is fed on a background dispatcher, a monotonically increasing
 * [renderTick] marks "something changed", and a single loop publishes at most one snapshot per
 * [FRAME_INTERVAL_MS]. [MutableStateFlow] conflation means ticks that arrive mid-frame collapse into
 * one, so a busy shell still renders at a steady ~60fps rather than thousands of times a second.
 *
 * The session owns its own [CoroutineScope] (not the ViewModel's), so it keeps running while the
 * terminal screen is off the back stack and survives ViewModel recreation.
 */
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

    /** Bumped whenever the emulator changes; the render loop samples this at frame cadence. */
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
                // Line-oriented events shouldn't occur on a PTY, but stay tolerant of a fallback repo.
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
        // Poll at frame cadence instead of collecting+delaying per tick — avoids falling behind when
        // output arrives faster than we can snapshot.
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

    /** Non-blocking keystroke path — returns false if the PTY is not ready yet. */
    fun offerInput(text: String): Boolean = repository.offerInput(text)

    /** Blocking fallback when [offerInput] returns false (session still starting). */
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

    /** Kill the shell and tear the session down. Idempotent from the caller's perspective. */
    fun destroy() {
        scope.launch { runCatching { repository.stop() } }
            .invokeOnCompletion { scope.cancel() }
    }

    private fun snapshotLocked(): TerminalScreen = synchronized(emulatorLock) { emulator.snapshot() }

    private companion object {
        /** ~60fps; the upper bound on how often the screen snapshot is republished. */
        const val FRAME_INTERVAL_MS = 16L
    }
}
