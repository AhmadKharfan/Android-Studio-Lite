package com.ahmadkharfan.androidstudiolite.feature.terminal
import androidx.lifecycle.viewModelScope
import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslTerminalLine
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslTerminalLineKind
import com.ahmadkharfan.androidstudiolite.domain.model.TerminalLineKind
import com.ahmadkharfan.androidstudiolite.domain.model.TerminalOutputLine
import com.ahmadkharfan.androidstudiolite.domain.repository.TerminalRepository
import kotlinx.coroutines.launch

private val INITIAL_LINES = listOf(
    AslTerminalLine("cd ~/projects/MyApplication", AslTerminalLineKind.Cmd),
    AslTerminalLine("./gradlew tasks --group build", AslTerminalLineKind.Cmd),
    AslTerminalLine("assemble - Assembles the outputs of this project."),
    AslTerminalLine("build - Assembles and tests this project."),
    AslTerminalLine("clean - Deletes the build directory."),
    AslTerminalLine("BUILD SUCCESSFUL in 2s", AslTerminalLineKind.Success),
)

private val INSERTABLE_KEYS = setOf("/", "|")

class TerminalViewModel(
    private val terminalRepository: TerminalRepository,
) : BaseViewModel<TerminalUiState, Nothing>(
    initialState = TerminalUiState(lines = INITIAL_LINES),
), TerminalInteractionListener {

    override fun onInputChanged(value: String) {
        updateState { copy(input = value) }
    }

    override fun onExtraKeyPressed(key: String) {
        if (key in INSERTABLE_KEYS) {
            updateState { copy(input = input + key) }
        }
    }

    override fun onNewSession() {
        updateState { copy(sessionNumber = sessionNumber + 1, lines = emptyList(), input = "") }
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
        viewModelScope.launch {
            val output = terminalRepository.execute(command)
            updateState { copy(lines = lines + output.map { line -> line.toUiLine() }, running = false) }
        }
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
