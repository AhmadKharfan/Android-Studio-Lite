package com.example.androidstudiolite.feature.terminal.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.core.designsystem.component.ide.AslTerminalLine
import com.example.androidstudiolite.core.designsystem.component.ide.AslTerminalLineKind
import com.example.androidstudiolite.domain.model.TerminalLineKind
import com.example.androidstudiolite.domain.model.TerminalOutputLine
import com.example.androidstudiolite.domain.usecase.ExecuteTerminalCommandUseCase
import com.example.androidstudiolite.feature.terminal.interaction.TerminalInteraction
import com.example.androidstudiolite.feature.terminal.uiState.TerminalUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    private val execute: ExecuteTerminalCommandUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TerminalUiState(lines = INITIAL_LINES))
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    fun onInteraction(interaction: TerminalInteraction) {
        when (interaction) {
            is TerminalInteraction.InputChanged -> _uiState.update { it.copy(input = interaction.value) }
            is TerminalInteraction.ExtraKeyPressed -> if (interaction.key in INSERTABLE_KEYS) {
                _uiState.update { it.copy(input = it.input + interaction.key) }
            }
            TerminalInteraction.NewSession -> _uiState.update {
                it.copy(sessionNumber = it.sessionNumber + 1, lines = emptyList(), input = "")
            }
            TerminalInteraction.SubmitCommand -> submitCommand()
        }
    }

    private fun submitCommand() {
        val command = _uiState.value.input.trim()
        if (command.isEmpty()) return
        if (command == "clear") {
            _uiState.update { it.copy(lines = emptyList(), input = "") }
            return
        }
        _uiState.update {
            it.copy(lines = it.lines + AslTerminalLine(command, AslTerminalLineKind.Cmd), input = "", running = true)
        }
        viewModelScope.launch {
            val output = execute(command)
            _uiState.update { it.copy(lines = it.lines + output.map { line -> line.toUiLine() }, running = false) }
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
