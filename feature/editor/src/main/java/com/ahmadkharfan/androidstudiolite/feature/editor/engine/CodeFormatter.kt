package com.ahmadkharfan.androidstudiolite.feature.editor.engine

/**
 * A conservative, lexer-aware re-indenter. It never rewrites the *content* of a line — it only fixes
 * leading indentation and trims trailing whitespace — and it refuses to touch any line that begins
 * inside a multi-line construct (block comment or Kotlin triple-quoted string) so significant
 * whitespace and comment art are preserved. Brace languages indent off `{ ( [`; XML off its tags.
 */
object CodeFormatter {

    fun reformat(text: String, tabSize: Int, language: EditorLanguage): String = when (language) {
        EditorLanguage.Xml -> reindentXml(text, tabSize)
        EditorLanguage.Plain -> text.lineSequence().joinToString("\n") { it.trimEnd() }
        else -> reindentBraces(text, tabSize)
    }

    private enum class Carry { Normal, BlockComment, TripleString }

    private fun reindentBraces(text: String, tabSize: Int): String {
        val unit = " ".repeat(tabSize.coerceAtLeast(1))
        val out = StringBuilder()
        var level = 0
        var carry = Carry.Normal
        val lines = text.split("\n")
        lines.forEachIndexed { index, raw ->
            val startCarry = carry
            val protectedLine = startCarry != Carry.Normal
            val leadingClosers = if (protectedLine) 0 else raw.trimStart().takeWhile { it in "})]" }.length
            val thisLevel = (level - leadingClosers).coerceAtLeast(0)

            val scan = scanBraceLine(raw, startCarry)
            carry = scan.carry

            if (protectedLine) {
                out.append(raw.trimEnd('\n', '\r'))
            } else {
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) out.append("") else out.append(unit.repeat(thisLevel)).append(trimmed)
            }
            level = (level + scan.delta).coerceAtLeast(0)
            if (index != lines.lastIndex) out.append("\n")
        }
        return out.toString()
    }

    private data class LineScan(val delta: Int, val carry: Carry)

    private fun scanBraceLine(line: String, startCarry: Carry): LineScan {
        var delta = 0
        var i = 0
        val n = line.length
        var state = startCarry
        // Transient in-line states (strings/char/line-comment) never carry past a newline.
        var inString = false
        var inChar = false
        var lineComment = false
        while (i < n) {
            val c = line[i]
            when {
                lineComment -> return LineScan(delta, Carry.Normal)
                state == Carry.BlockComment -> {
                    if (c == '*' && i + 1 < n && line[i + 1] == '/') { state = Carry.Normal; i += 2; continue }
                    i++
                }
                state == Carry.TripleString -> {
                    if (c == '"' && line.startsWith("\"\"\"", i)) { state = Carry.Normal; i += 3; continue }
                    i++
                }
                inString -> {
                    when (c) {
                        '\\' -> i += 2
                        '"' -> { inString = false; i++ }
                        else -> i++
                    }
                }
                inChar -> {
                    when (c) {
                        '\\' -> i += 2
                        '\'' -> { inChar = false; i++ }
                        else -> i++
                    }
                }
                else -> {
                    when {
                        c == '/' && i + 1 < n && line[i + 1] == '/' -> { lineComment = true; i += 2 }
                        c == '/' && i + 1 < n && line[i + 1] == '*' -> { state = Carry.BlockComment; i += 2 }
                        c == '"' && line.startsWith("\"\"\"", i) -> { state = Carry.TripleString; i += 3 }
                        c == '"' -> { inString = true; i++ }
                        c == '\'' -> { inChar = true; i++ }
                        c == '{' || c == '(' || c == '[' -> { delta++; i++ }
                        c == '}' || c == ')' || c == ']' -> { delta--; i++ }
                        else -> i++
                    }
                }
            }
        }
        val endCarry = when (state) {
            Carry.BlockComment -> Carry.BlockComment
            Carry.TripleString -> Carry.TripleString
            else -> Carry.Normal
        }
        return LineScan(delta, endCarry)
    }

    private fun reindentXml(text: String, tabSize: Int): String {
        val unit = " ".repeat(tabSize.coerceAtLeast(1))
        val out = StringBuilder()
        var level = 0
        var inComment = false
        val lines = text.split("\n")
        lines.forEachIndexed { index, raw ->
            val trimmed = raw.trim()
            if (inComment) {
                out.append(raw.trimEnd('\n', '\r'))
                if (trimmed.contains("-->")) inComment = false
                if (index != lines.lastIndex) out.append("\n")
                return@forEachIndexed
            }
            val startsWithClose = trimmed.startsWith("</") || trimmed.startsWith("/>")
            val thisLevel = if (startsWithClose) (level - 1).coerceAtLeast(0) else level
            if (trimmed.isEmpty()) out.append("") else out.append(unit.repeat(thisLevel)).append(trimmed)

            if (trimmed.startsWith("<!--") && !trimmed.contains("-->")) {
                inComment = true
            } else {
                val opens = OPEN_TAG.findAll(trimmed).count()
                val closes = CLOSE_TAG.findAll(trimmed).count()
                val selfClose = SELF_CLOSE.findAll(trimmed).count()
                level = (level + opens - closes - selfClose).coerceAtLeast(0)
            }
            if (index != lines.lastIndex) out.append("\n")
        }
        return out.toString()
    }

    private val OPEN_TAG = Regex("<[A-Za-z]")
    private val CLOSE_TAG = Regex("</")
    private val SELF_CLOSE = Regex("/>")
}
