package com.ahmadkharfan.androidstudiolite.feature.editor.engine
object KotlinLexUtil {
    private enum class StringScanState {
        Code,
        LineComment,
        BlockComment,
        String,
        TripleString,
        Character,
        StringTemplate,
    }

    private class StringScan(
        var state: StringScanState = StringScanState.Code,
        var templateDepth: Int = 0,
        var index: Int = 0,
    )

    fun isInsideStringLiteral(text: String, caret: Int): Boolean {
        if (caret < 0 || caret > text.length) return false
        val scan = StringScan()
        while (scan.index < caret) {
            advanceStringScan(text, scan)
        }
        return scan.index == caret && scan.state == StringScanState.String
    }

    private fun advanceStringScan(text: String, scan: StringScan) {
        when (scan.state) {
            StringScanState.Code -> scanCode(text, scan)
            StringScanState.LineComment -> scanLineComment(text, scan)
            StringScanState.BlockComment -> scanBlockComment(text, scan)
            StringScanState.String -> scanString(text, scan)
            StringScanState.TripleString -> scanTripleString(text, scan)
            StringScanState.Character -> scanCharacter(text, scan)
            StringScanState.StringTemplate -> scanStringTemplate(text, scan)
        }
    }

    private fun scanCode(text: String, scan: StringScan) {
        when {
            text.startsWith("//", scan.index) -> scan.moveTo(StringScanState.LineComment, 2)
            text.startsWith("/*", scan.index) -> scan.moveTo(StringScanState.BlockComment, 2)
            text.startsWith("\"\"\"", scan.index) -> scan.moveTo(StringScanState.TripleString, 3)
            text[scan.index] == '"' -> scan.moveTo(StringScanState.String)
            text[scan.index] == '\'' -> scan.moveTo(StringScanState.Character)
            else -> scan.index++
        }
    }

    private fun scanLineComment(text: String, scan: StringScan) {
        if (text[scan.index] == '\n') scan.state = StringScanState.Code
        scan.index++
    }

    private fun scanBlockComment(text: String, scan: StringScan) {
        if (text.startsWith("*/", scan.index)) {
            scan.moveTo(StringScanState.Code, 2)
        } else {
            scan.index++
        }
    }

    private fun scanTripleString(text: String, scan: StringScan) {
        if (text.startsWith("\"\"\"", scan.index)) {
            scan.moveTo(StringScanState.Code, 3)
        } else {
            scan.index++
        }
    }

    private fun scanCharacter(text: String, scan: StringScan) {
        when (text[scan.index]) {
            '\\' -> scan.index += 2
            '\'' -> scan.moveTo(StringScanState.Code)
            else -> scan.index++
        }
    }

    private fun scanString(text: String, scan: StringScan) {
        when {
            text[scan.index] == '\\' -> scan.index += 2
            text.startsWith("\${", scan.index) -> {
                scan.templateDepth = 1
                scan.moveTo(StringScanState.StringTemplate, 2)
            }
            text[scan.index] == '"' -> scan.moveTo(StringScanState.Code)
            else -> scan.index++
        }
    }

    private fun scanStringTemplate(text: String, scan: StringScan) {
        when (text[scan.index]) {
            '{' -> {
                scan.templateDepth++
                scan.index++
            }
            '}' -> {
                scan.templateDepth--
                scan.index++
                if (scan.templateDepth <= 0) scan.state = StringScanState.String
            }
            else -> scan.index++
        }
    }

    private fun StringScan.moveTo(nextState: StringScanState, consumedCharacters: Int = 1) {
        state = nextState
        index += consumedCharacters
    }
    fun isComposableStringExitPosition(text: String, caret: Int): Boolean {
        if (!isInsideStringLiteral(text, caret)) return false
        var j = caret
        while (j < text.length && text[j] != '"') {
            if (text[j] == '\\') j += 2 else j++
        }
        if (j >= text.length || text[j] != '"') return false
        var k = j + 1
        while (k < text.length && text[k].isWhitespace()) k++
        return k < text.length && text[k] == ')'
    }
    fun composableStringExitInsertOffset(text: String, caret: Int): Int? {
        if (!isComposableStringExitPosition(text, caret)) return null
        var j = caret
        while (j < text.length && text[j] != '"') {
            if (text[j] == '\\') j += 2 else j++
        }
        var k = j + 1
        while (k < text.length && text[k].isWhitespace()) k++
        return if (k < text.length && text[k] == ')') k + 1 else null
    }
    fun isDeclarationBeforeParen(text: String, nameStart: Int): Boolean {
        if (nameStart <= 0) return false
        val before = text.substring(0, nameStart).trimEnd()
        return DECLARATION_KEYWORDS.any { kw ->
            before.endsWith(kw) && (before.length == kw.length || !before[before.length - kw.length - 1].isLetterOrDigit())
        }
    }
    fun isEmptyFunctionBodyLine(text: String, caret: Int, prefixStart: Int): Boolean {
        if (prefixStart != caret) return false
        val lineStart = text.lastIndexOf('\n', caret - 1) + 1
        val lineBeforeCaret = text.substring(lineStart, caret)
        if (lineBeforeCaret.isNotEmpty() && !lineBeforeCaret.all { it.isWhitespace() }) return false
        return braceDepthBefore(text, caret) > 0
    }
    fun isDeclarationNamePosition(text: String, nameStart: Int): Boolean {
        if (nameStart <= 0) return false
        var i = nameStart - 1
        while (i >= 0 && (text[i] == ' ' || text[i] == '\t')) i--
        if (i < 0 || text[i] == '.') return false
        val end = i + 1
        var s = end
        while (s > 0 && (text[s - 1].isLetterOrDigit() || text[s - 1] == '_')) s--
        return text.substring(s, end) in DECLARATION_NAME_KEYWORDS
    }
    private val DECLARATION_NAME_KEYWORDS = setOf(
        "fun", "val", "var", "class", "object", "interface", "enum", "typealias", "annotation", "companion",
    )
    fun isInFunctionBody(text: String, caret: Int): Boolean = braceDepthBefore(text, caret) > 0
    fun braceDepthBefore(text: String, caret: Int): Int {
        var depth = 0
        var i = 0
        var inStr = false
        var inChar = false
        var raw = false
        while (i < caret) {
            when {
                !inStr && !inChar && !raw && text.startsWith("\"\"\"", i) -> { raw = true; i += 3 }
                raw -> {
                    if (text.startsWith("\"\"\"", i)) { raw = false; i += 3 } else i++
                }
                !inStr && !inChar && text.startsWith("//", i) -> {
                    i += 2
                    while (i < caret && text[i] != '\n') i++
                }
                !inStr && !inChar && text.startsWith("/*", i) -> {
                    i += 2
                    while (i < caret && !text.startsWith("*/", i)) i++
                    i += 2
                }
                inStr -> {
                    if (text[i] == '\\') i += 2
                    else if (text[i] == '"') { inStr = false; i++ }
                    else i++
                }
                inChar -> {
                    if (text[i] == '\\') i += 2
                    else if (text[i] == '\'') { inChar = false; i++ }
                    else i++
                }
                text[i] == '"' -> { inStr = true; i++ }
                text[i] == '\'' -> { inChar = true; i++ }
                text[i] == '{' -> { depth++; i++ }
                text[i] == '}' -> { depth--; i++ }
                else -> i++
            }
        }
        return depth
    }
    private val DECLARATION_KEYWORDS = listOf(
        "fun", "class", "data", "enum", "object", "interface", "constructor",
    )
    private fun keywordBefore(text: String, end: Int, word: String): Boolean {
        val start = end - word.length + 1
        if (start < 0) return false
        if (!text.regionMatches(start, word, 0, word.length, ignoreCase = true)) return false
        val before = start - 1
        return before < 0 || !text[before].isLetterOrDigit()
    }
}
