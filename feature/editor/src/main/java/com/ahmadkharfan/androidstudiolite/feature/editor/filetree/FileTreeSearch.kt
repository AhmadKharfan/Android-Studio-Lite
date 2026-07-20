package com.ahmadkharfan.androidstudiolite.feature.editor.filetree

import com.ahmadkharfan.androidstudiolite.feature.editor.EditorFileNodeUiModel

data class FileTreeSearchMatch(
    val id: String,
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val icon: String?,
)

/** Case-insensitive name search across the whole project tree. */
fun searchFileTree(
    nodes: List<EditorFileNodeUiModel>,
    query: String,
    pathPrefix: String = "",
): List<FileTreeSearchMatch> {
    val needle = query.trim()
    if (needle.isBlank()) return emptyList()
    val results = ArrayList<FileTreeSearchMatch>()
    collectMatches(nodes, pathPrefix, needle, results)
    return results.sortedWith(compareBy({ !it.isDirectory }, { it.relativePath.lowercase() }))
}

private fun collectMatches(
    nodes: List<EditorFileNodeUiModel>,
    pathPrefix: String,
    query: String,
    out: MutableList<FileTreeSearchMatch>,
) {
    nodes.forEach { node ->
        val segment = if (pathPrefix.isEmpty()) node.name else "$pathPrefix/${node.name}"
        val isDirectory = node.children != null
        if (node.name.contains(query, ignoreCase = true)) {
            out += FileTreeSearchMatch(
                id = node.id,
                name = node.name,
                relativePath = segment,
                isDirectory = isDirectory,
                icon = node.icon,
            )
        }
        node.children?.let { collectMatches(it, segment, query, out) }
    }
}

/** Folder ids that must be expanded to reveal [targetId] in the tree. */
fun ancestorFolderIds(nodes: List<EditorFileNodeUiModel>, targetId: String): Set<String> {
    fun walk(list: List<EditorFileNodeUiModel>, ancestors: List<String>): Set<String>? {
        for (node in list) {
            if (node.id == targetId) return ancestors.toSet()
            node.children?.let { children ->
                walk(children, ancestors + node.id)?.let { return it }
            }
        }
        return null
    }
    return walk(nodes, emptyList()).orEmpty()
}
