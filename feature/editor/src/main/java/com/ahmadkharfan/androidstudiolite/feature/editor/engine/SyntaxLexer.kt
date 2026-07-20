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
            EditorLanguage.Plain -> PlainLexer
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
    override fun tokenizeLine(line: String, entryState: LexerState): LineResult {
        val tokens = ArrayList<SyntaxToken>()
        val n = line.length
        var i = 0
        var state = entryState
        when (state) {
            LexerState.BlockComment -> {
                val close = line.indexOf("*/")
                if (close < 0) {
                    if (n > 0) tokens.add(SyntaxToken(0, n, TokenType.Comment))
                    return LineResult(tokens, LexerState.BlockComment)
                }
                tokens.add(SyntaxToken(0, close + 2, TokenType.Comment))
                i = close + 2
                state = LexerState.Default
            }
            LexerState.RawString -> {
                val close = line.indexOf("\"\"\"")
                if (close < 0) {
                    if (n > 0) tokens.add(SyntaxToken(0, n, TokenType.StringLiteral))
                    return LineResult(tokens, LexerState.RawString)
                }
                tokens.add(SyntaxToken(0, close + 3, TokenType.StringLiteral))
                i = close + 3
                state = LexerState.Default
            }
            LexerState.Default -> Unit
        }
        while (i < n) {
            val c = line[i]
            when {
                c == ' ' || c == '\t' -> i++
                c == '/' && i + 1 < n && line[i + 1] == '/' -> {
                    tokens.add(SyntaxToken(i, n, TokenType.Comment))
                    i = n
                }
                c == '/' && i + 1 < n && line[i + 1] == '*' -> {
                    val close = line.indexOf("*/", i + 2)
                    if (close < 0) {
                        tokens.add(SyntaxToken(i, n, TokenType.Comment))
                        i = n
                        state = LexerState.BlockComment
                    } else {
                        tokens.add(SyntaxToken(i, close + 2, TokenType.Comment))
                        i = close + 2
                    }
                }
                rawStrings && c == '"' && line.startsWith("\"\"\"", i) -> {
                    val close = line.indexOf("\"\"\"", i + 3)
                    if (close < 0) {
                        tokens.add(SyntaxToken(i, n, TokenType.StringLiteral))
                        i = n
                        state = LexerState.RawString
                    } else {
                        tokens.add(SyntaxToken(i, close + 3, TokenType.StringLiteral))
                        i = close + 3
                    }
                }
                c == '"' -> {
                    val end = readQuoted(line, i, '"')
                    tokens.add(SyntaxToken(i, end, TokenType.StringLiteral))
                    i = end
                }
                c == '\'' -> {
                    val end = readQuoted(line, i, '\'')
                    tokens.add(SyntaxToken(i, end, TokenType.StringLiteral))
                    i = end
                }
                c == '@' && i + 1 < n && (line[i + 1].isLetter() || line[i + 1] == '_') -> {
                    var j = i + 1
                    while (j < n && (line[j].isLetterOrDigit() || line[j] == '_' || line[j] == '.')) j++
                    tokens.add(SyntaxToken(i, j, TokenType.Annotation))
                    i = j
                }
                c.isDigit() -> {
                    var j = i + 1
                    while (j < n && (line[j].isLetterOrDigit() || line[j] == '.' || line[j] == '_')) j++
                    tokens.add(SyntaxToken(i, j, TokenType.Number))
                    i = j
                }
                c.isLetter() || c == '_' -> {
                    var j = i + 1
                    while (j < n && (line[j].isLetterOrDigit() || line[j] == '_')) j++
                    val word = line.substring(i, j)
                    val type = when {
                        word in keywords -> TokenType.Keyword
                        else -> {
                            var k = j
                            while (k < n && line[k] == ' ') k++
                            when {
                                k < n && line[k] == '(' -> TokenType.Function
                                word[0].isUpperCase() -> TokenType.Type
                                else -> TokenType.Variable
                            }
                        }
                    }
                    tokens.add(SyntaxToken(i, j, type))
                    i = j
                }
                else -> i++
            }
        }
        return LineResult(tokens, state)
    }
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
