package com.ahmadkharfan.androidstudiolite.feature.terminal

import androidx.lifecycle.viewModelScope
import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.domain.model.TerminalEvent
import com.ahmadkharfan.androidstudiolite.domain.repository.TerminalRepository
import com.ahmadkharfan.androidstudiolite.feature.terminal.emulator.TerminalEmulator
import com.ahmadkharfan.androidstudiolite.feature.terminal.emulator.TerminalScreen
import kotlinx.coroutines.launch

private const val DEFAULT_ROWS = 24
private const val DEFAULT_COLS = 80

class TerminalViewModel(
    private val terminalRepository: TerminalRepository,
) : BaseViewModel<TerminalUiState, Nothing>(
    initialState = TerminalUiState(screen = TerminalScreen.blank(DEFAULT_ROWS, DEFAULT_COLS)),
), TerminalInteractionListener {

    /** The emulator is the single source of truth for the screen; all access goes through [emulatorLock]. */
    private var emulator = TerminalEmulator(DEFAULT_ROWS, DEFAULT_COLS)
    private val emulatorLock = Any()

    init {
        // Subscribe before the session starts — the repository's stream has no replay buffer.
        observeSession()
        viewModelScope.launch { terminalRepository.start(rows = DEFAULT_ROWS, cols = DEFAULT_COLS) }
    }

    private fun observeSession() {
        viewModelScope.launch {
            terminalRepository.events.collect { event ->
                when (event) {
                    is TerminalEvent.Bytes -> pushScreen { feed(event.text) }
                    // Line-oriented events shouldn't occur on a PTY, but stay tolerant if a fallback repo is wired.
                    is TerminalEvent.Output -> pushScreen { feed(event.line.text + "\r\n") }
                    is TerminalEvent.CommandFinished -> Unit
                    TerminalEvent.SessionEnded -> updateState { copy(running = false) }
                }
            }
        }
    }

    private inline fun pushScreen(block: TerminalEmulator.() -> Unit) {
        val snapshot = synchronized(emulatorLock) {
            emulator.block()
            emulator.snapshot()
        }
        updateState { copy(screen = snapshot) }
    }

    override fun onKeyInput(text: String) {
        if (text.isEmpty()) return
        viewModelScope.launch { terminalRepository.writeInput(text) }
    }

    override fun onSpecialKey(key: TerminalKey) {
        viewModelScope.launch { terminalRepository.writeInput(TerminalKeys.bytes(key)) }
    }

    override fun onResize(rows: Int, cols: Int) {
        val r = rows.coerceAtLeast(1)
        val c = cols.coerceAtLeast(1)
        val snapshot = synchronized(emulatorLock) {
            if (emulator.rows == r && emulator.cols == c) return
            emulator.resize(r, c)
            emulator.snapshot()
        }
        updateState { copy(screen = snapshot) }
        viewModelScope.launch { terminalRepository.resize(r, c) }
    }

    override fun onExtraKeyPressed(key: String) {
        TerminalKeys.specialForExtraKey(key)?.let { onSpecialKey(it); return }
        TerminalKeys.textForExtraKey(key)?.let { onKeyInput(it) }
    }

    override fun onNewSession() {
        viewModelScope.launch {
            terminalRepository.stop()
            val (rows, cols) = synchronized(emulatorLock) {
                emulator = TerminalEmulator(emulator.rows, emulator.cols)
                emulator.rows to emulator.cols
            }
            updateState {
                copy(sessionNumber = sessionNumber + 1, screen = TerminalScreen.blank(rows, cols), running = true)
            }
            terminalRepository.start(rows = rows, cols = cols)
        }
    }
}
