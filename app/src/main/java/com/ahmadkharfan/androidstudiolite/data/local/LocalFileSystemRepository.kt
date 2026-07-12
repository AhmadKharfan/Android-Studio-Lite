package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeEvent
import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeType
import com.ahmadkharfan.androidstudiolite.domain.model.FolderNode
import com.ahmadkharfan.androidstudiolite.domain.model.FolderTree
import com.ahmadkharfan.androidstudiolite.domain.repository.FileSystemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Real [FileSystemRepository] for directory browsing (e.g. picking an external folder to import). Rooted
 * at [browseRoot]; [getFolderTree] returns a shallow, directory-only snapshot (root + two levels) and
 * [listChildren] lazily fetches deeper levels. Every [FolderNode.id] is an absolute path. Mutations
 * publish events on the shared [changeBus].
 */
class LocalFileSystemRepository(
    private val browseRoot: File,
    private val changeBus: FileChangeBus,
) : FileSystemRepository {

    override suspend fun getFolderTree(): FolderTree = withContext(Dispatchers.IO) {
        if (!browseRoot.exists()) browseRoot.mkdirs()
        FolderTree(
            breadcrumb = browseRoot.absolutePath.split(File.separatorChar).filter { it.isNotEmpty() },
            items = childDirs(browseRoot).map { it.toNode(depth = 1) },
        )
    }

    override suspend fun listChildren(path: String): List<FolderNode> = withContext(Dispatchers.IO) {
        childDirs(File(path)).map { FolderNode(id = it.absolutePath, name = it.name, children = null) }
    }

    override suspend fun createDirectory(parentPath: String, name: String): String = withContext(Dispatchers.IO) {
        val target = LocalFsSupport.childOf(File(parentPath), name)
        if (!target.mkdirs() && !target.isDirectory) throw IOException("Could not create directory: ${target.absolutePath}")
        changeBus.emit(FileChangeType.CREATED, target.absolutePath)
        target.absolutePath
    }

    override suspend fun delete(path: String): Unit = withContext(Dispatchers.IO) {
        val target = File(path)
        if (!target.exists()) return@withContext
        if (!target.deleteRecursively()) throw IOException("Could not delete: ${target.absolutePath}")
        changeBus.emit(FileChangeType.DELETED, target.absolutePath)
    }

    override suspend fun rename(path: String, newName: String): String = withContext(Dispatchers.IO) {
        val source = File(path)
        require(source.exists()) { "No such entry: $path" }
        val target = LocalFsSupport.childOf(source.parentFile ?: File("."), newName)
        require(!target.exists()) { "Target already exists: ${target.absolutePath}" }
        if (!source.renameTo(target)) throw IOException("Could not rename $path to $newName")
        changeBus.emit(FileChangeType.MOVED, path = target.absolutePath, oldPath = source.absolutePath)
        target.absolutePath
    }

    override suspend fun move(sourcePath: String, targetDir: String): String = withContext(Dispatchers.IO) {
        val source = File(sourcePath)
        require(source.exists()) { "No such entry: $sourcePath" }
        val dest = File(targetDir)
        require(dest.isDirectory) { "Not a directory: $targetDir" }
        require(!LocalFsSupport.isSameOrDescendant(source, dest)) { "Cannot move a directory into itself: $sourcePath" }
        val target = File(dest, source.name)
        require(!target.exists()) { "Target already exists: ${target.absolutePath}" }
        if (!source.renameTo(target)) throw IOException("Could not move $sourcePath into $targetDir")
        changeBus.emit(FileChangeType.MOVED, path = target.absolutePath, oldPath = source.absolutePath)
        target.absolutePath
    }

    override fun observeChanges(): Flow<FileChangeEvent> = changeBus.events

    private fun childDirs(dir: File): List<File> =
        LocalFsSupport.sortedChildren(dir).filter { it.isDirectory && !LocalFsSupport.isIgnoredDir(it) }

    private fun File.toNode(depth: Int): FolderNode {
        // Root + two visible levels keeps the initial browse cheap; deeper levels load via listChildren.
        val children = if (depth < 2) childDirs(this).map { it.toNode(depth + 1) } else null
        return FolderNode(id = absolutePath, name = name, children = children)
    }
}
