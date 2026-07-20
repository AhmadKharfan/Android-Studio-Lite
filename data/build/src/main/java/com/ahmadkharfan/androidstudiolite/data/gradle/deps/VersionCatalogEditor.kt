package com.ahmadkharfan.androidstudiolite.data.gradle.deps

object VersionCatalogEditor {

    sealed interface Result {
        data class Changed(val text: String) : Result
        data class Unchanged(val reason: String) : Result
        data class Failure(val reason: String) : Result
    }

    fun addLibrary(text: String, alias: String, group: String, name: String, version: String?): Result {
        val normalized = normalize(alias)
        if (libraryAliases(text).any { normalize(it) == normalized }) {
            return Result.Unchanged("Library alias '$alias' already present")
        }
        val coordinate = if (version.isNullOrBlank()) "$group:$name" else "$group:$name:$version"
        val entry = "$alias = \"$coordinate\""
        return Result.Changed(insertIntoTable(text, "libraries", entry))
    }

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

        var insertAt = lines.size
        for (i in headerIdx + 1 until lines.size) {
            if (lines[i].trim().startsWith("[")) { insertAt = i; break }
        }

        while (insertAt > headerIdx + 1 && lines[insertAt - 1].isBlank()) insertAt--
        lines.add(insertAt, entry)
        return lines.joinToString("\n")
    }

    private fun normalize(alias: String): String = alias.replace('.', '-').replace('_', '-')
}
