package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeEvent
import com.ahmadkharfan.androidstudiolite.domain.model.FolderNode
import com.ahmadkharfan.androidstudiolite.domain.model.FolderTree
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface FileSystemRepository {
    suspend fun getFolderTree(): FolderTree

    suspend fun listChildren(path: String): List<FolderNode> = emptyList()

    suspend fun createDirectory(parentPath: String, name: String): String =
        throw UnsupportedOperationException()

    suspend fun delete(path: String): Unit = throw UnsupportedOperationException()

    suspend fun rename(path: String, newName: String): String =
        throw UnsupportedOperationException()

    suspend fun move(sourcePath: String, targetDir: String): String =
        throw UnsupportedOperationException()

    fun observeChanges(): Flow<FileChangeEvent> = emptyFlow()
}
