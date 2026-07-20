package com.ahmadkharfan.androidstudiolite.data.gradle.parse

import com.ahmadkharfan.androidstudiolite.data.gradle.model.CatalogLibrary
import com.ahmadkharfan.androidstudiolite.data.gradle.model.CatalogPlugin
import com.ahmadkharfan.androidstudiolite.data.gradle.model.VersionCatalog

object VersionCatalogParser {

    private enum class Section { NONE, VERSIONS, LIBRARIES, PLUGINS, BUNDLES }

    fun parse(text: CharSequence): VersionCatalog {
        val versions = LinkedHashMap<String, String>()
        val rawLibraries = ArrayList<Pair<String, String>>()
        val rawPlugins = ArrayList<Pair<String, String>>()
        val bundles = LinkedHashMap<String, List<String>>()

        var section = Section.NONE
        for (line in logicalLines(text.toString())) {
            val trimmed = stripComment(line).trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("[")) {
                section = when (trimmed.trim('[', ']').trim()) {
                    "versions" -> Section.VERSIONS
                    "libraries" -> Section.LIBRARIES
                    "plugins" -> Section.PLUGINS
                    "bundles" -> Section.BUNDLES
                    else -> Section.NONE
                }
                continue
            }
            val eq = trimmed.indexOf('=')
            if (eq < 0) continue
            val key = trimmed.substring(0, eq).trim().trim('"')
            val value = trimmed.substring(eq + 1).trim()
            when (section) {
                Section.VERSIONS -> versions[key] = unquote(value)
                Section.LIBRARIES -> rawLibraries += key to value
                Section.PLUGINS -> rawPlugins += key to value
                Section.BUNDLES -> bundles[key] = parseStringArray(value)
                Section.NONE -> {}
            }
        }

        val libraries = rawLibraries.map { (alias, value) -> resolveLibrary(alias, value, versions) }
        val plugins = rawPlugins.map { (alias, value) -> resolvePlugin(alias, value, versions) }
        return VersionCatalog(versions, libraries, plugins, bundles)
    }

    private fun resolveLibrary(alias: String, value: String, versions: Map<String, String>): CatalogLibrary {
        if (value.startsWith("\"") || value.startsWith("'")) {

            val parts = unquote(value).split(':')
            return CatalogLibrary(
                alias = alias,
                group = parts.getOrNull(0),
                name = parts.getOrNull(1),
                version = parts.getOrNull(2),
            )
        }
        val fields = parseInlineTable(value)
        var group = fields["group"]
        var name = fields["name"]
        fields["module"]?.let {
            val idx = it.indexOf(':')
            if (idx > 0) { group = it.substring(0, idx); name = it.substring(idx + 1) }
        }
        return CatalogLibrary(alias, group, name, resolveVersionField(fields, versions))
    }

    private fun resolvePlugin(alias: String, value: String, versions: Map<String, String>): CatalogPlugin {
        if (value.startsWith("\"") || value.startsWith("'")) {
            val raw = unquote(value)
            val idx = raw.indexOf(':')
            return if (idx > 0) CatalogPlugin(alias, raw.substring(0, idx), raw.substring(idx + 1))
            else CatalogPlugin(alias, raw, null)
        }
        val fields = parseInlineTable(value)
        return CatalogPlugin(alias, fields["id"] ?: "", resolveVersionField(fields, versions))
    }

    private fun resolveVersionField(fields: Map<String, String>, versions: Map<String, String>): String? {
        fields["version.ref"]?.let { return versions[it] ?: fields["version.ref"] }
        fields["version"]?.let { return it }
        return null
    }

    private fun parseInlineTable(value: String): Map<String, String> {
        val inner = value.trim().removePrefix("{").removeSuffix("}").trim()
        if (inner.isEmpty()) return emptyMap()
        val map = LinkedHashMap<String, String>()
        for (part in splitTopLevel(inner, ',')) {
            val eq = part.indexOf('=')
            if (eq < 0) continue
            val k = part.substring(0, eq).trim().trim('"')
            val v = unquote(part.substring(eq + 1).trim())
            map[k] = v
        }
        return map
    }

    private fun parseStringArray(value: String): List<String> {
        val inner = value.trim().removePrefix("[").removeSuffix("]")
        return splitTopLevel(inner, ',').map { unquote(it.trim()) }.filter { it.isNotEmpty() }
    }

    private fun splitTopLevel(s: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var depth = 0
        var quote: Char? = null
        for (c in s) {
            when {
                quote != null -> { sb.append(c); if (c == quote) quote = null }
                c == '"' || c == '\'' -> { quote = c; sb.append(c) }
                c == '[' || c == '{' -> { depth++; sb.append(c) }
                c == ']' || c == '}' -> { depth--; sb.append(c) }
                c == sep && depth == 0 -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
        }
        if (sb.isNotBlank()) out += sb.toString()
        return out
    }

    private fun logicalLines(text: String): List<String> = text.split('\n').map { it.trimEnd('\r') }

    private fun stripComment(line: String): String {
        var quote: Char? = null
        for (i in line.indices) {
            val c = line[i]
            when {
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' -> quote = c
                c == '#' -> return line.substring(0, i)
            }
        }
        return line
    }

    private fun unquote(s: String): String {
        val t = s.trim()
        if (t.length >= 2 && (t.first() == '"' || t.first() == '\'') && t.last() == t.first()) {
            return t.substring(1, t.length - 1)
        }
        return t
    }
}
