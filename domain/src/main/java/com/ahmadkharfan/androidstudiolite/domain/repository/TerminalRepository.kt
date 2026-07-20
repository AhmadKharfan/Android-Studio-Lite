package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.TerminalEvent
import kotlinx.coroutines.flow.Flow

interface TerminalRepository {

    val events: Flow<TerminalEvent>

    suspend fun start(workingDirectory: String? = null, rows: Int = 24, cols: Int = 80)

    suspend fun send(command: String)

    suspend fun writeInput(text: String) {}

    fun offerInput(text: String): Boolean = false

    suspend fun resize(rows: Int, cols: Int) {}

    suspend fun stop()
}
