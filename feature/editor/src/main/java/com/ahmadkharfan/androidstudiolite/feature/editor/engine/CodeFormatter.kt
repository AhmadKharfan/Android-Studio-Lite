package com.ahmadkharfan.androidstudiolite.feature.editor.engine

object CodeFormatter {

    fun reformat(text: String, tabSize: Int, language: EditorLanguage): String = when (language) {
        EditorLanguage.Xml -> reindentXml(text, tabSize)
        EditorLanguage.Plain, EditorLanguage.Markdown ->
            text.lineSequence().joinToString("\n") { it.trimEnd() }
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
        val scan = BraceLineScan(carry = startCarry)
        var delta = 0

        while (scan.index < line.length) {
            if (skipLiteralOrComment(line, scan)) continue
            val c = line[scan.index]
            if (c == '{' || c == '(' || c == '[') delta++
            if (c == '}' || c == ')' || c == ']') delta--
            scan.index++
        }
        return LineScan(delta, scan.carry)
    }

    private class BraceLineScan(
        var carry: Carry,
        var index: Int = 0,
        var inString: Boolean = false,
        var inChar: Boolean = false,
    )

    private fun skipLiteralOrComment(line: String, scan: BraceLineScan): Boolean = when {
        scan.carry == Carry.BlockComment -> {
            skipBlockComment(line, scan)
            true
        }
        scan.carry == Carry.TripleString -> {
            skipTripleString(line, scan)
            true
        }
        scan.inString -> {
            skipQuotedLiteral(line, scan, '"')
            true
        }
        scan.inChar -> {
            skipQuotedLiteral(line, scan, '\'')
            true
        }
        else -> startLiteralOrComment(line, scan)
    }

    private fun skipBlockComment(line: String, scan: BraceLineScan) {
        if (line.startsWith("*/", scan.index)) {
            scan.carry = Carry.Normal
            scan.index += 2
        } else {
            scan.index++
        }
    }

    private fun skipTripleString(line: String, scan: BraceLineScan) {
        if (line.startsWith("\"\"\"", scan.index)) {
            scan.carry = Carry.Normal
            scan.index += 3
        } else {
            scan.index++
        }
    }

    private fun skipQuotedLiteral(line: String, scan: BraceLineScan, quote: Char) {
        if (line[scan.index] == '\\') {
            scan.index += 2
            return
        }
        if (line[scan.index] == quote) {
            if (quote == '"') scan.inString = false else scan.inChar = false
        }
        scan.index++
    }

    private fun startLiteralOrComment(line: String, scan: BraceLineScan): Boolean {
        when {
            line.startsWith("//", scan.index) -> scan.index = line.length
            line.startsWith("/*", scan.index) -> {
                scan.carry = Carry.BlockComment
                scan.index += 2
            }
            line.startsWith("\"\"\"", scan.index) -> {
                scan.carry = Carry.TripleString
                scan.index += 3
            }
            line[scan.index] == '"' -> {
                scan.inString = true
                scan.index++
            }
            line[scan.index] == '\'' -> {
                scan.inChar = true
                scan.index++
            }
            else -> return false
        }
        return true
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
