package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeEvent
import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeType
import com.ahmadkharfan.androidstudiolite.domain.model.FileNode
import com.ahmadkharfan.androidstudiolite.domain.repository.FileTreeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Real [FileTreeRepository] over [java.io.File]. A project's id is its directory name under
 * [projectsRoot] (absolute ids are also accepted, for callers that already hold a path); every emitted
 * [FileNode.id] is an absolute path, which is the same key [LocalFileContentRepository] and the mutation
 * methods here accept. Mutations publish events on the shared [changeBus].
 *
 * git status is intentionally left null here — that is populated by the git integration (task T5).
 */
class LocalFileTreeRepository(
    private val projectsRoot: File,
    private val changeBus: FileChangeBus,
) : FileTreeRepository {

    override suspend fun getFileTree(projectId: String): List<FileNode> = withContext(Dispatchers.IO) {
        val root = resolveProjectRoot(projectId)
        if (!root.isDirectory) emptyList()
        else LocalFsSupport.sortedChildren(root)
            .filterNot { LocalFsSupport.isIgnoredDir(it) }
            .map { it.toNode(depth = 1) }
    }

    override suspend fun listChildren(path: String): List<FileNode> = withContext(Dispatchers.IO) {
        val dir = File(path)
        if (!dir.isDirectory) emptyList()
        else LocalFsSupport.sortedChildren(dir)
            .filterNot { LocalFsSupport.isIgnoredDir(it) }
            // One level only: children carry null so callers know to lazily fetch on expand.
            .map { FileNode(id = it.absolutePath, name = it.name, children = if (it.isDirectory) null else null) }
    }

    override suspend fun createFile(parentPath: String, name: String): String = withContext(Dispatchers.IO) {
        val target = LocalFsSupport.childOf(File(parentPath), name)
        if (!target.createNewFile()) throw IOException("Could not create file: ${target.absolutePath}")
        changeBus.emit(FileChangeType.CREATED, target.absolutePath)
        target.absolutePath
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

    override suspend fun duplicate(path: String): String = withContext(Dispatchers.IO) {
        val source = File(path)
        require(source.exists()) { "No such file: $path" }
        source.parentFile ?: throw IOException("Cannot copy project root")
        val target = LocalFsSupport.uniqueCopyTarget(source)
        copyEntry(source, target)
        target.absolutePath
    }

    override suspend fun copy(path: String, newParentPath: String): String = withContext(Dispatchers.IO) {
        val source = File(path)
        require(source.exists()) { "No such file: $path" }
        val newParent = File(newParentPath)
        require(newParent.isDirectory) { "Not a directory: $newParentPath" }
        require(!LocalFsSupport.isSameOrDescendant(source, newParent)) {
            "Cannot copy a directory into itself: $path"
        }
        val target = LocalFsSupport.uniqueCopyTarget(source, newParent)
        copyEntry(source, target)
        target.absolutePath
    }

    private suspend fun copyEntry(source: File, target: File) {
        if (source.isDirectory) {
            val copied = source.copyRecursively(target = target, overwrite = false)
            if (!copied) throw IOException("Could not copy: ${source.absolutePath}")
        } else {
            source.copyTo(target = target, overwrite = false)
        }
        changeBus.emit(FileChangeType.CREATED, target.absolutePath)
    }

    override suspend fun rename(path: String, newName: String): String = withContext(Dispatchers.IO) {
        val source = File(path)
        require(source.exists()) { "No such file: $path" }
        val target = LocalFsSupport.childOf(source.parentFile ?: File("."), newName)
        require(!target.exists()) { "Target already exists: ${target.absolutePath}" }
        if (!source.renameTo(target)) throw IOException("Could not rename $path to $newName")
        changeBus.emit(FileChangeType.MOVED, path = target.absolutePath, oldPath = source.absolutePath)
        target.absolutePath
    }

    override suspend fun move(path: String, newParentPath: String): String = withContext(Dispatchers.IO) {
        val source = File(path)
        require(source.exists()) { "No such file: $path" }
        val newParent = File(newParentPath)
        require(newParent.isDirectory) { "Not a directory: $newParentPath" }
        require(!LocalFsSupport.isSameOrDescendant(source, newParent)) {
            "Cannot move a directory into itself: $path"
        }
        val target = File(newParent, source.name)
        require(!target.exists()) { "Target already exists: ${target.absolutePath}" }
        if (!source.renameTo(target)) throw IOException("Could not move $path into $newParentPath")
        changeBus.emit(FileChangeType.MOVED, path = target.absolutePath, oldPath = source.absolutePath)
        target.absolutePath
    }

    override fun observeChanges(): Flow<FileChangeEvent> = changeBus.events

    /** Accepts either a bare directory name under [projectsRoot] or an already-absolute project path. */
    private fun resolveProjectRoot(projectId: String): File {
        val asAbsolute = File(projectId)
        return if (asAbsolute.isAbsolute && asAbsolute.exists()) asAbsolute else File(projectsRoot, projectId)
    }

    private fun File.toNode(depth: Int): FileNode {
        val children = if (isDirectory && depth < LocalFsSupport.MAX_TREE_DEPTH) {
            LocalFsSupport.sortedChildren(this)
                .filterNot { LocalFsSupport.isIgnoredDir(it) }
                .map { it.toNode(depth + 1) }
        } else if (isDirectory) {
            emptyList()
        } else {
            null
        }
        return FileNode(id = absolutePath, name = name, children = children)
    }
}
