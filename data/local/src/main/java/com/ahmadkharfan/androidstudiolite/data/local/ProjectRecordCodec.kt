package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.Project

internal object ProjectRecordCodec {

    fun encode(projects: List<Project>): String =
        projects.joinToString("\n") { p ->
            listOf(
                p.id, p.name, p.path, p.language, (p.lastOpenedMillis ?: 0L).toString(),
                p.packageName.orEmpty(), p.buildable.toString(),
            ).joinToString("\t") { escape(it) }
        }

    fun decode(raw: String): List<Project> =
        if (raw.isEmpty()) emptyList()
        else raw.split("\n").mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.size < 5) return@mapNotNull null
            Project(
                id = unescape(parts[0]),
                name = unescape(parts[1]),
                path = unescape(parts[2]),
                language = unescape(parts[3]),
                lastOpenedMillis = unescape(parts[4]).toLongOrNull()?.takeIf { it > 0L },
                packageName = parts.getOrNull(5)?.let(::unescape)?.takeIf { it.isNotBlank() },
                buildable = parts.getOrNull(6)?.let(::unescape)?.toBooleanStrictOrNull() ?: true,
            )
        }

    fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    fun unescape(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    't' -> out.append('\t')
                    'n' -> out.append('\n')
                    '\\' -> out.append('\\')
                    else -> out.append(s[i + 1])
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
