package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeEvent
import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeType
import com.ahmadkharfan.androidstudiolite.domain.model.FileTooLargeException
import com.ahmadkharfan.androidstudiolite.domain.repository.FileContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

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
