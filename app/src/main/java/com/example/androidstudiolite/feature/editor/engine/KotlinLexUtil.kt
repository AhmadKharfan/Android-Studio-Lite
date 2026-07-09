package com.example.androidstudiolite.feature.editor.engine
object KotlinLexUtil {
    fun isInsideStringLiteral(text: String, caret: Int): Boolean {
        if (caret < 0 || caret > text.length) return false
        var state = 0
        var templateDepth = 0
        var i = 0
        var caretInString = false
        while (i < caret) {
            when (state) {
                0 -> when {
                    text.startsWith("//", i) -> { state = 1; i += 2 }
                    text.startsWith("/*", i) -> { state = 2; i += 2 }
                    text.startsWith("\"\"\"", i) -> { state = 4; i += 3 }
                    text[i] == '"' -> { state = 3; i++ }
                    text[i] == '\'' -> { state = 5; i++ }
                    else -> i++
                }
                1 -> { if (text[i] == '\n') state = 0; i++ }
                2 -> {
                    if (text.startsWith("*/", i)) { state = 0; i += 2 } else i++
                }
                4 -> {
                    if (text.startsWith("\"\"\"", i)) { state = 0; i += 3 } else i++
                }
                5 -> {
                    if (text[i] == '\\') i += 2
                    else if (text[i] == '\'') { state = 0; i++ }
                    else i++
                }
                3 -> when {
                    text[i] == '\\' -> i += 2
                    text.startsWith("\${", i) -> { state = 6; templateDepth = 1; i += 2 }
                    text[i] == '"' -> { state = 0; i++ }
                    else -> i++
                }
                6 -> when {
                    text[i] == '{' -> { templateDepth++; i++ }
                    text[i] == '}' -> {
                        templateDepth--
                        i++
                        if (templateDepth <= 0) state = 3
                    }
                    else -> i++
                }
            }
        }
        if (i == caret && state == 3) caretInString = true
        return caretInString
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
