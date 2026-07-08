package com.example.androidstudiolite.domain.repository

import com.example.androidstudiolite.domain.model.TerminalOutputLine

interface TerminalRepository {
    suspend fun execute(command: String): List<TerminalOutputLine>
}
