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

    private enum class BraceScanState { Code, String, Character, TripleString }

    private class BraceScan(
        var state: BraceScanState = BraceScanState.Code,
        var index: Int = 0,
        var depth: Int = 0,
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
        val scan = BraceScan()
        while (scan.index < caret) {
            advanceBraceScan(text, caret, scan)
        }
        return scan.depth
    }

    private fun advanceBraceScan(text: String, caret: Int, scan: BraceScan) {
        when (scan.state) {
            BraceScanState.Code -> scanBraceCode(text, caret, scan)
            BraceScanState.String -> scanQuotedBraceText(text, scan, '"')
            BraceScanState.Character -> scanQuotedBraceText(text, scan, '\'')
            BraceScanState.TripleString -> scanTripleBraceText(text, scan)
        }
    }

    private fun scanBraceCode(text: String, caret: Int, scan: BraceScan) {
        when {
            text.startsWith("\"\"\"", scan.index) -> scan.moveTo(BraceScanState.TripleString, 3)
            text.startsWith("//", scan.index) -> {
                scan.index += 2
                while (scan.index < caret && text[scan.index] != '\n') scan.index++
            }
            text.startsWith("/*", scan.index) -> {
                scan.index += 2
                while (scan.index < caret && !text.startsWith("*/", scan.index)) scan.index++
                scan.index += 2
            }
            text[scan.index] == '"' -> scan.moveTo(BraceScanState.String)
            text[scan.index] == '\'' -> scan.moveTo(BraceScanState.Character)
            text[scan.index] == '{' -> { scan.depth++; scan.index++ }
            text[scan.index] == '}' -> { scan.depth--; scan.index++ }
            else -> scan.index++
        }
    }

    private fun scanQuotedBraceText(text: String, scan: BraceScan, quote: Char) {
        when (text[scan.index]) {
            '\\' -> scan.index += 2
            quote -> scan.moveTo(BraceScanState.Code)
            else -> scan.index++
        }
    }

    private fun scanTripleBraceText(text: String, scan: BraceScan) {
        if (text.startsWith("\"\"\"", scan.index)) {
            scan.moveTo(BraceScanState.Code, 3)
        } else {
            scan.index++
        }
    }

    private fun BraceScan.moveTo(nextState: BraceScanState, consumedCharacters: Int = 1) {
        state = nextState
        index += consumedCharacters
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
