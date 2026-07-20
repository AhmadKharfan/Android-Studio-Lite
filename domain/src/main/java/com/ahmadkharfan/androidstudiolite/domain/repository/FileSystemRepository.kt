package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeEvent
import com.ahmadkharfan.androidstudiolite.domain.model.FolderNode
import com.ahmadkharfan.androidstudiolite.domain.model.FolderTree
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Directory-browsing surface used by the folder picker (e.g. choosing an external folder to import).
 * The real implementation is backed by [java.io.File]; [FolderNode.id] carries the absolute path of the
 * directory so callers can act on a selection without re-deriving its location.
 *
 * The mutation/observation members have default implementations so the in-memory fakes stay valid; the
 * real [com.ahmadkharfan.androidstudiolite.data.local.LocalFileSystemRepository] overrides them all.
 */
interface FileSystemRepository {
    /** A bounded snapshot of the browse root and its top levels for initial display. */
    suspend fun getFolderTree(): FolderTree

    /** Lazily lists the immediate sub-directories of [path] (absolute path). */
    suspend fun listChildren(path: String): List<FolderNode> = emptyList()

    /** Creates a directory named [name] under [parentPath]; returns the new directory's absolute path. */
    suspend fun createDirectory(parentPath: String, name: String): String =
        throw UnsupportedOperationException()

    /** Deletes the file or directory at [path] (recursively for directories). */
    suspend fun delete(path: String): Unit = throw UnsupportedOperationException()

    /** Renames the entry at [path] to [newName] in place; returns the new absolute path. */
    suspend fun rename(path: String, newName: String): String =
        throw UnsupportedOperationException()

    /** Moves the entry at [sourcePath] into directory [targetDir]; returns the new absolute path. */
    suspend fun move(sourcePath: String, targetDir: String): String =
        throw UnsupportedOperationException()

    /** A hot stream of filesystem change events produced by this data layer's mutations. */
    fun observeChanges(): Flow<FileChangeEvent> = emptyFlow()
}
