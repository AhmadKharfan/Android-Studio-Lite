package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeEvent
import com.ahmadkharfan.androidstudiolite.domain.model.FileNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The project file tree shown in the editor. The real implementation resolves [projectId] to a real
 * directory under the on-device projects root and walks it; every [FileNode.id] is the entry's absolute
 * path, which is the same key accepted by [FileContentRepository] and by the mutation methods here.
 *
 * CRUD + change-observation members default to no-ops/errors so the in-memory fakes remain valid; the
 * real [com.ahmadkharfan.androidstudiolite.data.local.LocalFileTreeRepository] overrides them.
 */
interface FileTreeRepository {
    /** A bounded, recursive snapshot of the project's files and directories (build/VCS dirs elided). */
    suspend fun getFileTree(projectId: String): List<FileNode>

    /** Lazily lists the immediate children of the directory at [path] (absolute path). */
    suspend fun listChildren(path: String): List<FileNode> = emptyList()

    /** Creates an empty file named [name] under [parentPath]; returns its absolute path. */
    suspend fun createFile(parentPath: String, name: String): String =
        throw UnsupportedOperationException()

    /** Creates a directory named [name] under [parentPath]; returns its absolute path. */
    suspend fun createDirectory(parentPath: String, name: String): String =
        throw UnsupportedOperationException()

    /** Deletes the file or directory at [path] (recursively for directories). */
    suspend fun delete(path: String): Unit = throw UnsupportedOperationException()

    /** Copies the file or directory at [path] into the same parent with a unique name; returns the new absolute path. */
    suspend fun duplicate(path: String): String = throw UnsupportedOperationException()

    /** Copies the file or directory at [path] into [newParentPath] with a unique name; returns the new absolute path. */
    suspend fun copy(path: String, newParentPath: String): String = throw UnsupportedOperationException()

    /** Renames the entry at [path] to [newName] in place; returns the new absolute path. */
    suspend fun rename(path: String, newName: String): String =
        throw UnsupportedOperationException()

    /** Moves the entry at [path] into the directory [newParentPath]; returns the new absolute path. */
    suspend fun move(path: String, newParentPath: String): String =
        throw UnsupportedOperationException()

    /** A hot stream of filesystem change events produced by this data layer's mutations. */
    fun observeChanges(): Flow<FileChangeEvent> = emptyFlow()
}
