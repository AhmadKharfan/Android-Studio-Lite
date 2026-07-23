package com.ahmadkharfan.androidstudiolite.feature.editor.filetree

import com.ahmadkharfan.androidstudiolite.domain.model.FileNode
import com.ahmadkharfan.androidstudiolite.feature.editor.EditorFileNodeUiModel
import java.io.File

private val CODE_FILE_EXTENSIONS = setOf("kt", "kts", "java", "xml", "gradle")

internal fun isCodeFile(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in CODE_FILE_EXTENSIONS

internal fun defaultExpandedIds(nodes: List<FileNode>): Set<String> {
    val topLevelDirs = nodes.filter { it.children != null }.map { it.id }
    val trailDirs = pathToDefaultFile(nodes)?.dropLast(1)?.map { it.id }.orEmpty()
    return (topLevelDirs + trailDirs).toSet()
}

internal fun firstOpenableFile(nodes: List<FileNode>): FileNode? = pathToDefaultFile(nodes)?.lastOrNull()

internal fun pathToDefaultFile(nodes: List<FileNode>): List<FileNode>? =
    pathToFile(nodes) { it.name.equals("MainActivity.kt", true) || it.name.equals("MainActivity.java", true) }
        ?: pathToFile(nodes) { isCodeFile(it.name) }
        ?: pathToFile(nodes) { true }

internal fun pathToFile(
    nodes: List<FileNode>,
    trail: List<FileNode> = emptyList(),
    predicate: (FileNode) -> Boolean,
): List<FileNode>? {
    for (node in nodes) {
        val nextTrail = trail + node
        val children = node.children
        if (children == null) {
            if (predicate(node)) return nextTrail
        } else {
            pathToFile(children, nextTrail, predicate)?.let { return it }
        }
    }
    return null
}

internal fun findFileTreeNode(nodes: List<EditorFileNodeUiModel>, id: String): EditorFileNodeUiModel? {
    for (node in nodes) {
        if (node.id == id) return node
        node.children?.let { children ->
            findFileTreeNode(children, id)?.let { return it }
        }
    }
    return null
}

internal fun isValidFileTreeName(name: String): Boolean =
    name.isNotBlank() && !name.contains('/') && !name.contains('\\') && name != "." && name != ".."

internal fun isSameOrChild(parent: String, path: String): Boolean =
    path == parent || path.startsWith(parent.trimEnd('/') + "/")

internal fun renamedPathFor(path: String, oldPath: String, newPath: String): String =
    if (path == oldPath) newPath else newPath.trimEnd('/') + path.removePrefix(oldPath).let {
        if (it.startsWith("/")) it else "/$it"
    }

internal fun breadcrumbFor(projectRoot: String?, id: String, name: String): List<String> {
    val relative = if (projectRoot != null && id.startsWith(projectRoot)) {
        id.removePrefix(projectRoot).trimStart('/')
    } else {
        id
    }
    val parts = relative.split('/').filter { it.isNotEmpty() }
    return if (parts.size > 1) parts else listOf(name)
}

internal fun canonicalPath(path: String): String = runCatching { File(path).canonicalPath }
    .getOrDefault(File(path).absolutePath)
