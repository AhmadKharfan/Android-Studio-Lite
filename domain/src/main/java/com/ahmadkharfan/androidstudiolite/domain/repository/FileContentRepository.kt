package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

interface FileContentRepository {
    suspend fun readText(path: String): String
    suspend fun writeText(path: String, text: String)

    suspend fun lastModifiedMillis(path: String): Long = 0L

    fun observeChanges(): Flow<FileChangeEvent> = emptyFlow()

    fun rootInvalidationGenerations(): StateFlow<Map<String, Long>> = EMPTY_GENERATIONS

    private companion object {
        val EMPTY_GENERATIONS = MutableStateFlow<Map<String, Long>>(emptyMap())
    }
}
