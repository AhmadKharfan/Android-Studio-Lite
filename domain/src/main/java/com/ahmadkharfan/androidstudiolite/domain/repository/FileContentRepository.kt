package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Reads and writes text file contents by absolute path. The real implementation reads/writes UTF-8 and
 * guards against loading files above an editor size limit (throwing
 * [com.ahmadkharfan.androidstudiolite.domain.model.FileTooLargeException]).
 *
 * Metadata/observation members default to no-ops so the in-memory fake stays valid; the real
 * [com.ahmadkharfan.androidstudiolite.data.local.LocalFileContentRepository] overrides them.
 */
interface FileContentRepository {
    suspend fun readText(path: String): String
    suspend fun writeText(path: String, text: String)

    /** The file's last-modified time in epoch millis, or 0 if it does not exist. */
    suspend fun lastModifiedMillis(path: String): Long = 0L

    /** A hot stream of change events; used by the editor to detect edits made outside it. */
    fun observeChanges(): Flow<FileChangeEvent> = emptyFlow()

    /** Latest root-invalidation generation per canonical working-tree path. */
    fun rootInvalidationGenerations(): StateFlow<Map<String, Long>> = EMPTY_GENERATIONS

    private companion object {
        val EMPTY_GENERATIONS = MutableStateFlow<Map<String, Long>>(emptyMap())
    }
}
