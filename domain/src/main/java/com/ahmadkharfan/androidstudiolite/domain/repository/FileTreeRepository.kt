package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeEvent
import com.ahmadkharfan.androidstudiolite.domain.model.FileNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface FileTreeRepository {
    suspend fun getFileTree(projectId: String): List<FileNode>

    suspend fun listChildren(path: String): List<FileNode> = emptyList()

    suspend fun createFile(parentPath: String, name: String): String =
        throw UnsupportedOperationException()

    suspend fun createDirectory(parentPath: String, name: String): String =
        throw UnsupportedOperationException()

    suspend fun delete(path: String): Unit = throw UnsupportedOperationException()

    suspend fun duplicate(path: String): String = throw UnsupportedOperationException()

    suspend fun copy(path: String, newParentPath: String): String = throw UnsupportedOperationException()

    suspend fun rename(path: String, newName: String): String =
        throw UnsupportedOperationException()

    suspend fun move(path: String, newParentPath: String): String =
        throw UnsupportedOperationException()

    fun observeChanges(): Flow<FileChangeEvent> = emptyFlow()
}
