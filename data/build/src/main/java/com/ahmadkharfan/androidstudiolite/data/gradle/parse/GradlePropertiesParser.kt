package com.ahmadkharfan.androidstudiolite.data.gradle.parse

object GradlePropertiesParser {

    fun parse(text: CharSequence): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        val lines = joinContinuations(text.toString().split('\n'))
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue
            val sep = firstUnescapedSeparator(line)
            if (sep < 0) {
                if (line.isNotEmpty()) result[unescape(line)] = ""
                continue
            }
            val key = unescape(line.substring(0, sep).trim())
            val value = unescape(line.substring(sep + 1).trim())
            if (key.isNotEmpty()) result[key] = value
        }
        return result
    }

    fun gradleVersionFromWrapper(properties: Map<String, String>): String? {
        val url = properties["distributionUrl"] ?: return null
        return Regex("""gradle-([0-9]+(?:\.[0-9]+)*(?:-[A-Za-z0-9]+)?)-(?:all|bin)\.zip""")
            .find(url)?.groupValues?.get(1)
    }

    private fun joinContinuations(lines: List<String>): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        for (line in lines) {
            val trimmedEnd = line.trimEnd('\r')
            if (endsWithOddBackslash(trimmedEnd)) {
                sb.append(trimmedEnd.dropLast(1))
            } else {
                sb.append(trimmedEnd)
                out += sb.toString()
                sb.clear()
            }
        }
        if (sb.isNotEmpty()) out += sb.toString()
        return out
    }

    private fun endsWithOddBackslash(s: String): Boolean {
        var count = 0
        var i = s.length - 1
        while (i >= 0 && s[i] == '\\') { count++; i-- }
        return count % 2 == 1
    }

    private fun firstUnescapedSeparator(line: String): Int {
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\\') { i += 2; continue }
            if (c == '=' || c == ':') return i
            i++
        }
        return -1
    }

    private fun unescape(s: String): String {
        if (!s.contains('\\')) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                    else -> sb.append(n)
                }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }
}
