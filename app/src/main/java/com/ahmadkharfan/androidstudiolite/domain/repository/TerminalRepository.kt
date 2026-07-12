package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.TerminalOutputLine

interface TerminalRepository {
    suspend fun execute(command: String): List<TerminalOutputLine>
}
