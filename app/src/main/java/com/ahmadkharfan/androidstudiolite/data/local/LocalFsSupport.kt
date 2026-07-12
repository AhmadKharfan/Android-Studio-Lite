package com.ahmadkharfan.androidstudiolite.data.local

import java.io.File

/**
 * Shared filesystem helpers for the local data layer: consistent child ordering, the set of directories
 * that never belong in a project tree, and safe move/rename resolution. Kept in one place so the
 * filesystem, file-tree and content repositories behave identically.
 */
internal object LocalFsSupport {

    /** Max bytes the editor will load as text; larger files are refused to avoid OOM. */
    const val MAX_TEXT_FILE_BYTES: Long = 5L * 1024 * 1024

    /** How deep [buildFileNodes] walks before it stops recursing (defensive against pathological trees). */
    const val MAX_TREE_DEPTH: Int = 16

    /**
     * Directories that are build output or tooling caches — huge, machine-generated, and never useful to
     * browse in the editor, so they are elided from project trees.
     */
    private val IGNORED_DIR_NAMES = setOf(
        ".git", ".gradle", ".idea", "build", ".cxx", "caches", ".kotlin",
    )

    /** Directories first, then files; each group case-insensitively by name — the usual IDE ordering. */
    fun sortedChildren(dir: File): List<File> {
        val children = dir.listFiles()?.asList() ?: emptyList()
        return children.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() },
        )
    }

    fun isIgnoredDir(file: File): Boolean = file.isDirectory && file.name in IGNORED_DIR_NAMES

    /**
     * Ensures [name] is a single, safe path segment (no separators or traversal) and resolves it against
     * [parent]. Throws [IllegalArgumentException] otherwise so callers can't escape the intended dir.
     */
    fun childOf(parent: File, name: String): File {
        require(name.isNotBlank()) { "Name must not be blank" }
        require(!name.contains('/') && !name.contains('\\')) { "Name must be a single path segment: $name" }
        require(name != "." && name != "..") { "Invalid name: $name" }
        return File(parent, name)
    }

    /** True if [candidate] is [ancestor] itself or nested anywhere beneath it. */
    fun isSameOrDescendant(ancestor: File, candidate: File): Boolean {
        val a = ancestor.canonicalFile
        var cur: File? = candidate.canonicalFile
        while (cur != null) {
            if (cur == a) return true
            cur = cur.parentFile
        }
        return false
    }
}
