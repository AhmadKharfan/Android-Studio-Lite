package com.example.androidstudiolite.domain.usecase

import com.example.androidstudiolite.domain.model.TerminalOutputLine
import com.example.androidstudiolite.domain.repository.TerminalRepository

class ExecuteTerminalCommandUseCase(private val repository: TerminalRepository) {
    suspend operator fun invoke(command: String): List<TerminalOutputLine> = repository.execute(command)
}
