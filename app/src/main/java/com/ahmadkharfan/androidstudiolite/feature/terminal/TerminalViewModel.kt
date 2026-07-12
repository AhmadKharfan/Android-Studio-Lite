package com.ahmadkharfan.androidstudiolite.feature.terminal
import androidx.lifecycle.viewModelScope
import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslTerminalLine
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslTerminalLineKind
import com.ahmadkharfan.androidstudiolite.domain.model.TerminalEvent
import com.ahmadkharfan.androidstudiolite.domain.model.TerminalLineKind
import com.ahmadkharfan.androidstudiolite.domain.model.TerminalOutputLine
import com.ahmadkharfan.androidstudiolite.domain.repository.TerminalRepository
import kotlinx.coroutines.launch

private val INITIAL_LINES = listOf(
    AslTerminalLine("AndroidStudioLite shell — line-oriented (no PTY yet).", AslTerminalLineKind.Stdout),
)

private val INSERTABLE_KEYS = setOf("/", "|")

class TerminalViewModel(
    private val terminalRepository: TerminalRepository,
) : BaseViewModel<TerminalUiState, Nothing>(
    initialState = TerminalUiState(lines = INITIAL_LINES),
), TerminalInteractionListener {

    init {
        // Subscribe before the session starts — the repository's stream has no replay buffer.
        observeSession()
        viewModelScope.launch { terminalRepository.start() }
    }

    private fun observeSession() {
        viewModelScope.launch {
            terminalRepository.events.collect { event ->
                when (event) {
                    is TerminalEvent.Output ->
                        updateState { copy(lines = lines + event.line.toUiLine()) }

                    is TerminalEvent.CommandFinished ->
                        updateState { copy(running = false) }

                    TerminalEvent.SessionEnded ->
                        updateState {
                            copy(
                                running = false,
                                lines = lines + AslTerminalLine("[process exited]", AslTerminalLineKind.Stderr),
                            )
                        }
                }
            }
        }
    }

    override fun onInputChanged(value: String) {
        updateState { copy(input = value) }
    }

    override fun onExtraKeyPressed(key: String) {
        if (key in INSERTABLE_KEYS) {
            updateState { copy(input = input + key) }
        }
    }

    override fun onNewSession() {
        viewModelScope.launch {
            terminalRepository.stop()
            updateState {
                copy(sessionNumber = sessionNumber + 1, lines = emptyList(), input = "", running = false)
            }
            terminalRepository.start()
        }
    }

    override fun onSubmitCommand() {
        submitCommand()
    }

    private fun submitCommand() {
        val command = state.value.input.trim()
        if (command.isEmpty()) return
        if (command == "clear") {
            updateState { copy(lines = emptyList(), input = "") }
            return
        }
        updateState {
            copy(lines = lines + AslTerminalLine(command, AslTerminalLineKind.Cmd), input = "", running = true)
        }
        viewModelScope.launch { terminalRepository.send(command) }
    }

    private fun TerminalOutputLine.toUiLine() = AslTerminalLine(
        text = text,
        kind = when (kind) {
            TerminalLineKind.Stdout -> AslTerminalLineKind.Stdout
            TerminalLineKind.Stderr -> AslTerminalLineKind.Stderr
            TerminalLineKind.Success -> AslTerminalLineKind.Success
        },
    )
}
