package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeEvent
import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeType
import com.ahmadkharfan.androidstudiolite.domain.model.RootInvalidationReason
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide bus for filesystem change events. A single instance is shared (via Koin) by the local
 * filesystem, file-tree and file-content repositories so that a mutation made through any of them is
 * visible to observers of all of them — the editor watches it to detect external edits, the file tree
 * watches it to refresh after create/delete/rename/move.
 *
 * This reflects mutations that flow through the app's own data layer; it is not an OS inotify watcher.
 */
class FileChangeBus {
    // A replay-less buffered flow: late collectors don't re-see history, but bursts of events (e.g. a
    // recursive delete) won't be dropped under normal load. tryEmit never suspends the caller.
    private val _events = MutableSharedFlow<FileChangeEvent>(extraBufferCapacity = 128)
    private val _generations = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val generationLock = Any()

    val events: SharedFlow<FileChangeEvent> = _events.asSharedFlow()
    val generations: StateFlow<Map<String, Long>> = _generations.asStateFlow()

    fun emit(type: FileChangeType, path: String, oldPath: String? = null) {
        _events.tryEmit(FileChangeEvent.PathChanged(type = type, path = path, oldPath = oldPath))
    }

    /** Emits a lossless root invalidation and advances that root's durable in-process generation. */
    suspend fun emitRootInvalidated(root: String, reason: RootInvalidationReason) {
        val normalizedRoot = canonicalPath(root)
        val generation = synchronized(generationLock) {
            val next = (_generations.value[normalizedRoot] ?: 0L) + 1L
            _generations.value = _generations.value + (normalizedRoot to next)
            next
        }
        _events.emit(FileChangeEvent.RootInvalidated(normalizedRoot, generation, reason))
    }

    private fun canonicalPath(path: String): String = runCatching { java.io.File(path).canonicalPath }
        .getOrDefault(java.io.File(path).absolutePath)
}
