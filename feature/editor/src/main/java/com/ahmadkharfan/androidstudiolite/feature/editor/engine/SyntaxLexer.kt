package com.ahmadkharfan.androidstudiolite.feature.editor.engine
enum class LexerState {
    Default,
    BlockComment,
    RawString,
}
data class LineResult(val tokens: List<SyntaxToken>, val endState: LexerState)
interface SyntaxLexer {
    val initialState: LexerState get() = LexerState.Default
    fun tokenizeLine(line: String, entryState: LexerState): LineResult
    companion object {
        fun forLanguage(language: EditorLanguage): SyntaxLexer = when (language) {
            EditorLanguage.Kotlin -> CLikeLexer(KOTLIN_KEYWORDS, rawStrings = true)
            EditorLanguage.Java -> CLikeLexer(JAVA_KEYWORDS, rawStrings = false)
            EditorLanguage.Xml -> XmlLexer
            EditorLanguage.Markdown, EditorLanguage.Plain -> PlainLexer
        }
    }
}
object PlainLexer : SyntaxLexer {
    override fun tokenizeLine(line: String, entryState: LexerState): LineResult {
        val tokens = if (line.isEmpty()) emptyList() else listOf(SyntaxToken(0, line.length, TokenType.Plain))
        return LineResult(tokens, LexerState.Default)
    }
}
class CLikeLexer(
    private val keywords: Set<String>,
    private val rawStrings: Boolean,
) : SyntaxLexer {
    private data class TokenStep(
        val nextIndex: Int,
        val endState: LexerState? = null,
        val lineComplete: Boolean = false,
    )

    override fun tokenizeLine(line: String, entryState: LexerState): LineResult {
        val tokens = ArrayList<SyntaxToken>()
        val entryStep = resumeEntryState(line, entryState, tokens)
        if (entryStep.lineComplete) return LineResult(tokens, entryStep.endState ?: entryState)
        var i = entryStep.nextIndex
        var state = entryStep.endState ?: entryState
        while (i < line.length) {
            val c = line[i]
            val step = when {
                c == ' ' || c == '\t' -> skipWhitespace(i)
                c == '/' && i + 1 < line.length && line[i + 1] == '/' -> tokenizeLineComment(line, i, tokens)
                c == '/' && i + 1 < line.length && line[i + 1] == '*' -> tokenizeBlockComment(line, i, tokens)
                rawStrings && c == '"' && line.startsWith("\"\"\"", i) -> tokenizeRawString(line, i, tokens)
                c == '"' -> tokenizeQuotedString(line, i, '"', tokens)
                c == '\'' -> tokenizeQuotedString(line, i, '\'', tokens)
                c == '@' && i + 1 < line.length && (line[i + 1].isLetter() || line[i + 1] == '_') ->
                    tokenizeAnnotation(line, i, tokens)
                c.isDigit() -> tokenizeNumber(line, i, tokens)
                c.isLetter() || c == '_' -> tokenizeIdentifier(line, i, tokens)
                else -> skipSymbol(i)
            }
            i = step.nextIndex
            step.endState?.let { state = it }
        }
        return LineResult(tokens, state)
    }

    private fun resumeEntryState(
        line: String,
        entryState: LexerState,
        tokens: MutableList<SyntaxToken>,
    ): TokenStep = when (entryState) {
        LexerState.BlockComment -> resumeBlockComment(line, tokens)
        LexerState.RawString -> resumeRawString(line, tokens)
        LexerState.Default -> TokenStep(0, LexerState.Default)
    }

    private fun resumeBlockComment(line: String, tokens: MutableList<SyntaxToken>): TokenStep {
        val close = line.indexOf("*/")
        return if (close < 0) {
            if (line.isNotEmpty()) tokens.add(SyntaxToken(0, line.length, TokenType.Comment))
            TokenStep(line.length, LexerState.BlockComment, lineComplete = true)
        } else {
            tokens.add(SyntaxToken(0, close + 2, TokenType.Comment))
            TokenStep(close + 2, LexerState.Default)
        }
    }

    private fun resumeRawString(line: String, tokens: MutableList<SyntaxToken>): TokenStep {
        val close = line.indexOf("\"\"\"")
        return if (close < 0) {
            if (line.isNotEmpty()) tokens.add(SyntaxToken(0, line.length, TokenType.StringLiteral))
            TokenStep(line.length, LexerState.RawString, lineComplete = true)
        } else {
            tokens.add(SyntaxToken(0, close + 3, TokenType.StringLiteral))
            TokenStep(close + 3, LexerState.Default)
        }
    }

    private fun skipWhitespace(index: Int): TokenStep = TokenStep(index + 1)

    private fun tokenizeLineComment(
        line: String,
        start: Int,
        tokens: MutableList<SyntaxToken>,
    ): TokenStep {
        tokens.add(SyntaxToken(start, line.length, TokenType.Comment))
        return TokenStep(line.length)
    }

    private fun tokenizeBlockComment(
        line: String,
        start: Int,
        tokens: MutableList<SyntaxToken>,
    ): TokenStep {
        val close = line.indexOf("*/", start + 2)
        return if (close < 0) {
            tokens.add(SyntaxToken(start, line.length, TokenType.Comment))
            TokenStep(line.length, LexerState.BlockComment)
        } else {
            tokens.add(SyntaxToken(start, close + 2, TokenType.Comment))
            TokenStep(close + 2)
        }
    }

    private fun tokenizeRawString(
        line: String,
        start: Int,
        tokens: MutableList<SyntaxToken>,
    ): TokenStep {
        val close = line.indexOf("\"\"\"", start + 3)
        return if (close < 0) {
            tokens.add(SyntaxToken(start, line.length, TokenType.StringLiteral))
            TokenStep(line.length, LexerState.RawString)
        } else {
            tokens.add(SyntaxToken(start, close + 3, TokenType.StringLiteral))
            TokenStep(close + 3)
        }
    }

    private fun tokenizeQuotedString(
        line: String,
        start: Int,
        quote: Char,
        tokens: MutableList<SyntaxToken>,
    ): TokenStep {
        val end = readQuoted(line, start, quote)
        tokens.add(SyntaxToken(start, end, TokenType.StringLiteral))
        return TokenStep(end)
    }

    private fun tokenizeAnnotation(
        line: String,
        start: Int,
        tokens: MutableList<SyntaxToken>,
    ): TokenStep {
        var end = start + 1
        while (end < line.length && (line[end].isLetterOrDigit() || line[end] == '_' || line[end] == '.')) end++
        tokens.add(SyntaxToken(start, end, TokenType.Annotation))
        return TokenStep(end)
    }

    private fun tokenizeNumber(
        line: String,
        start: Int,
        tokens: MutableList<SyntaxToken>,
    ): TokenStep {
        var end = start + 1
        while (end < line.length && (line[end].isLetterOrDigit() || line[end] == '.' || line[end] == '_')) end++
        tokens.add(SyntaxToken(start, end, TokenType.Number))
        return TokenStep(end)
    }

    private fun tokenizeIdentifier(
        line: String,
        start: Int,
        tokens: MutableList<SyntaxToken>,
    ): TokenStep {
        var end = start + 1
        while (end < line.length && (line[end].isLetterOrDigit() || line[end] == '_')) end++
        val word = line.substring(start, end)
        val type = if (word in keywords) TokenType.Keyword else classifyIdentifier(line, end, word)
        tokens.add(SyntaxToken(start, end, type))
        return TokenStep(end)
    }

    private fun classifyIdentifier(line: String, end: Int, word: String): TokenType {
        var next = end
        while (next < line.length && line[next] == ' ') next++
        return when {
            next < line.length && line[next] == '(' -> TokenType.Function
            word[0].isUpperCase() -> TokenType.Type
            else -> TokenType.Variable
        }
    }

    private fun skipSymbol(index: Int): TokenStep = TokenStep(index + 1)

    private fun readQuoted(line: String, start: Int, quote: Char): Int {
        var i = start + 1
        val n = line.length
        while (i < n) {
            when (line[i]) {
                '\\' -> i += 2
                quote -> return i + 1
                else -> i++
            }
        }
        return n
    }
}
object XmlLexer : SyntaxLexer {
    override fun tokenizeLine(line: String, entryState: LexerState): LineResult {
        val tokens = ArrayList<SyntaxToken>()
        val n = line.length
        var i = 0
        var state = entryState
        if (state == LexerState.BlockComment) {
            val close = line.indexOf("-->")
            if (close < 0) {
                if (n > 0) tokens.add(SyntaxToken(0, n, TokenType.Comment))
                return LineResult(tokens, LexerState.BlockComment)
            }
            tokens.add(SyntaxToken(0, close + 3, TokenType.Comment))
            i = close + 3
            state = LexerState.Default
        }
        while (i < n) {
            if (line[i] == '<') {
                if (line.startsWith("<!--", i)) {
                    val close = line.indexOf("-->", i + 4)
                    if (close < 0) {
                        tokens.add(SyntaxToken(i, n, TokenType.Comment))
                        i = n
                        state = LexerState.BlockComment
                    } else {
                        tokens.add(SyntaxToken(i, close + 3, TokenType.Comment))
                        i = close + 3
                    }
                    continue
                }
                val gt = line.indexOf('>', i)
                val tagEnd = if (gt < 0) n else gt + 1
                tokenizeTag(line, i, tagEnd, tokens)
                i = tagEnd
                continue
            }
            val lt = line.indexOf('<', i)
            i = if (lt < 0) n else lt
        }
        return LineResult(tokens, state)
    }
    private fun tokenizeTag(line: String, start: Int, end: Int, tokens: MutableList<SyntaxToken>) {
        var i = start + 1
        if (i < end && line[i] == '/') i++
        val nameStart = i
        while (i < end && isNameChar(line[i])) i++
        if (i > nameStart) tokens.add(SyntaxToken(nameStart, i, TokenType.Type))
        while (i < end) {
            val c = line[i]
            when {
                c == '"' || c == '\'' -> {
                    var j = i + 1
                    while (j < end && line[j] != c) j++
                    val stop = minOf(j + 1, end)
                    tokens.add(SyntaxToken(i, stop, TokenType.StringLiteral))
                    i = stop
                }
                c.isLetter() || c == '_' -> {
                    val attrStart = i
                    while (i < end && isNameChar(line[i])) i++
                    tokens.add(SyntaxToken(attrStart, i, TokenType.Variable))
                }
                else -> i++
            }
        }
    }
    private fun isNameChar(c: Char): Boolean =
        c.isLetterOrDigit() || c == '_' || c == '-' || c == '.' || c == ':'
}
private val KOTLIN_KEYWORDS = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
    "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias",
    "typeof", "val", "var", "when", "while", "by", "catch", "constructor", "delegate", "dynamic", "field",
    "file", "finally", "get", "import", "init", "param", "property", "receiver", "set", "setparam",
    "value", "where", "abstract", "actual", "annotation", "companion", "const", "crossinline", "data",
    "enum", "expect", "external", "final", "infix", "inline", "inner", "internal", "lateinit", "noinline",
    "open", "operator", "out", "override", "private", "protected", "public", "reified", "sealed",
    "suspend", "tailrec", "vararg",
)
private val JAVA_KEYWORDS = setOf(
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
    "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for",
    "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new",
    "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super",
    "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while",
    "var", "record", "sealed", "permits", "yield", "true", "false", "null",
)
