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

class FileChangeBus {


    private val _events = MutableSharedFlow<FileChangeEvent>(extraBufferCapacity = 128)
    private val _generations = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val generationLock = Any()

    val events: SharedFlow<FileChangeEvent> = _events.asSharedFlow()
    val generations: StateFlow<Map<String, Long>> = _generations.asStateFlow()

    fun emit(type: FileChangeType, path: String, oldPath: String? = null) {
        _events.tryEmit(FileChangeEvent.PathChanged(type = type, path = path, oldPath = oldPath))
    }

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
