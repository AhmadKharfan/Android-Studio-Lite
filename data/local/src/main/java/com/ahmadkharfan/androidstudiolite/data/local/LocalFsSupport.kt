package com.ahmadkharfan.androidstudiolite.data.local

import java.io.File

internal object LocalFsSupport {

    const val MAX_TEXT_FILE_BYTES: Long = 5L * 1024 * 1024

    const val MAX_TREE_DEPTH: Int = 64

    private val IGNORED_DIR_NAMES = setOf(
        ".git", ".gradle", ".idea", "build", ".cxx", "caches", ".kotlin",
    )

    fun sortedChildren(dir: File): List<File> {
        val children = dir.listFiles()?.asList() ?: emptyList()
        return children.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() },
        )
    }

    fun isIgnoredDir(file: File): Boolean = file.isDirectory && file.name in IGNORED_DIR_NAMES

    fun childOf(parent: File, name: String): File {
        require(name.isNotBlank()) { "Name must not be blank" }
        require(!name.contains('/') && !name.contains('\\')) { "Name must be a single path segment: $name" }
        require(name != "." && name != "..") { "Invalid name: $name" }
        return File(parent, name)
    }

    fun uniqueCopyTarget(source: File, parent: File = source.parentFile ?: throw IllegalArgumentException("Cannot copy an entry without a parent")): File {
        val dotIndex = source.name.lastIndexOf('.')
        val hasExtension = !source.isDirectory && dotIndex > 0
        val baseName = if (hasExtension) source.name.substring(0, dotIndex) else source.name
        val extension = if (hasExtension) source.name.substring(dotIndex + 1) else ""
        fun candidate(index: Int): File {
            val suffix = if (index == 1) " copy" else " copy $index"
            val name = if (extension.isBlank()) "$baseName$suffix" else "$baseName$suffix.$extension"
            return File(parent, name)
        }
        var index = 1
        var target = candidate(index)
        while (target.exists()) {
            index++
            target = candidate(index)
        }
        return target
    }

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
