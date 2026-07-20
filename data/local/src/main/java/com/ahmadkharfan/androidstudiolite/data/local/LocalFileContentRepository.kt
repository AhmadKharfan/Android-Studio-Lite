package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeEvent
import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeType
import com.ahmadkharfan.androidstudiolite.domain.model.FileTooLargeException
import com.ahmadkharfan.androidstudiolite.domain.repository.FileContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Real [FileContentRepository] over [java.io.File]. Reads and writes UTF-8 by absolute path, refuses to
 * load files above [LocalFsSupport.MAX_TEXT_FILE_BYTES] (throwing [FileTooLargeException]), creates
 * parent directories on write, and publishes a [FileChangeType.CREATED] or [FileChangeType.MODIFIED]
 * event on the shared bus after every successful write so the editor can refresh open tabs and the
 * project tree live.
 */
class LocalFileContentRepository(
    private val changeBus: FileChangeBus,
    private val maxBytes: Long = LocalFsSupport.MAX_TEXT_FILE_BYTES,
) : FileContentRepository {

    override suspend fun readText(path: String): String = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext ""
        val size = file.length()
        if (size > maxBytes) throw FileTooLargeException(path = path, sizeBytes = size, limitBytes = maxBytes)
        file.readText(Charsets.UTF_8)
    }

    override suspend fun writeText(path: String, text: String): Unit = withContext(Dispatchers.IO) {
        val file = File(path)
        file.parentFile?.let { if (!it.exists()) it.mkdirs() }
        val existed = file.exists()
        file.writeText(text, Charsets.UTF_8)
        changeBus.emit(if (existed) FileChangeType.MODIFIED else FileChangeType.CREATED, file.absolutePath)
    }

    override suspend fun lastModifiedMillis(path: String): Long = withContext(Dispatchers.IO) {
        File(path).lastModified()
    }

    override fun observeChanges(): Flow<FileChangeEvent> = changeBus.events
}
