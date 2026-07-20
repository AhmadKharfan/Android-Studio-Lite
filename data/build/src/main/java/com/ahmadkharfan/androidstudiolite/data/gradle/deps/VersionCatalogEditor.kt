package com.ahmadkharfan.androidstudiolite.data.gradle.deps

/**
 * Structured text edits to a `libs.versions.toml`. Pure string→string transforms with explicit
 * outcomes — no file I/O, so they're trivially testable and the caller controls persistence.
 *
 * These edits are intentionally conservative: they add/remove whole lines under the relevant table
 * and never reformat the rest of the file.
 */
object VersionCatalogEditor {

    sealed interface Result {
        data class Changed(val text: String) : Result
        /** The requested edit was a no-op (e.g. alias already present / absent). */
        data class Unchanged(val reason: String) : Result
        data class Failure(val reason: String) : Result
    }

    /**
     * Adds a `[libraries]` entry `alias = "group:name:version"`. If [version] is non-null it is
     * emitted inline (shorthand form) rather than as a `version.ref`, to keep the edit self-contained.
     * Fails if [alias] already exists.
     */
    fun addLibrary(text: String, alias: String, group: String, name: String, version: String?): Result {
        val normalized = normalize(alias)
        if (libraryAliases(text).any { normalize(it) == normalized }) {
            return Result.Unchanged("Library alias '$alias' already present")
        }
        val coordinate = if (version.isNullOrBlank()) "$group:$name" else "$group:$name:$version"
        val entry = "$alias = \"$coordinate\""
        return Result.Changed(insertIntoTable(text, "libraries", entry))
    }

    /** Removes the `[libraries]` entry whose alias matches [alias] (separator-insensitive). */
    fun removeLibrary(text: String, alias: String): Result {
        val normalized = normalize(alias)
        val lines = text.split('\n').toMutableList()
        var section: String? = null
        val idx = lines.indexOfFirst { raw ->
            val t = raw.trim()
            if (t.startsWith("[")) { section = t.trim('[', ']').trim(); return@indexOfFirst false }
            section == "libraries" && aliasOf(t)?.let { normalize(it) == normalized } == true
        }
        if (idx < 0) return Result.Unchanged("Library alias '$alias' not found")
        lines.removeAt(idx)
        return Result.Changed(lines.joinToString("\n"))
    }

    /** Existing library aliases (keys in the `[libraries]` table). */
    fun libraryAliases(text: String): List<String> {
        val out = ArrayList<String>()
        var section: String? = null
        for (raw in text.split('\n')) {
            val t = raw.trim()
            when {
                t.startsWith("[") -> section = t.trim('[', ']').trim()
                section == "libraries" -> aliasOf(t)?.let { out += it }
            }
        }
        return out
    }

    private fun aliasOf(line: String): String? {
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("[")) return null
        val eq = line.indexOf('=')
        if (eq <= 0) return null
        return line.substring(0, eq).trim().trim('"').takeIf { it.isNotEmpty() }
    }

    /**
     * Inserts [entry] as the last line of the `[table]` section, appending the section (with a
     * leading blank line) if it doesn't exist yet.
     */
    private fun insertIntoTable(text: String, table: String, entry: String): String {
        val lines = text.split('\n').toMutableList()
        val header = "[$table]"
        val headerIdx = lines.indexOfFirst { it.trim() == header }
        if (headerIdx < 0) {
            val sb = StringBuilder(text.trimEnd('\n'))
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append(header).append('\n').append(entry).append('\n')
            return sb.toString()
        }
        // Find the end of this section: the next line that starts a new table.
        var insertAt = lines.size
        for (i in headerIdx + 1 until lines.size) {
            if (lines[i].trim().startsWith("[")) { insertAt = i; break }
        }
        // Back up over trailing blank lines so the entry stays inside the section.
        while (insertAt > headerIdx + 1 && lines[insertAt - 1].isBlank()) insertAt--
        lines.add(insertAt, entry)
        return lines.joinToString("\n")
    }

    private fun normalize(alias: String): String = alias.replace('.', '-').replace('_', '-')
}
