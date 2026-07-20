package com.ahmadkharfan.androidstudiolite.data.gradle.parse

/**
 * A tiny, tolerant lexer for Gradle build scripts, shared by the Groovy and Kotlin-DSL parsers.
 *
 * It is deliberately *not* a real Groovy/Kotlin parser — build scripts are Turing complete and we
 * only need to recover the declarative shape (blocks, calls, string literals). The scanner strips
 * comments, understands the three string flavors, and tracks brace/paren nesting via a flat token
 * stream so the higher-level parsers can locate `android { … }`, `dependencies { … }`, etc.
 */
enum class GTokenType {
    IDENT, STRING, NUMBER,
    LBRACE, RBRACE, LPAREN, RPAREN, LBRACKET, RBRACKET,
    DOT, COMMA, EQ, NEWLINE, OTHER,
}

data class GToken(
    val type: GTokenType,
    /** Raw source text. For [GTokenType.STRING] this includes the surrounding quotes. */
    val text: String,
    val start: Int,
    val end: Int,
) {
    /** For a STRING token, the content with quotes stripped (best effort, no unescaping). */
    fun stringValue(): String {
        if (type != GTokenType.STRING) return text
        var s = text
        for (q in listOf("\"\"\"", "'''")) {
            if (s.length >= 6 && s.startsWith(q) && s.endsWith(q)) return s.substring(3, s.length - 3)
        }
        if (s.length >= 2 && (s[0] == '"' || s[0] == '\'') && s.last() == s[0]) {
            s = s.substring(1, s.length - 1)
        }
        return s
    }
}

object GradleScriptScanner {

    fun tokenize(text: CharSequence): List<GToken> {
        val out = ArrayList<GToken>()
        val len = text.length
        var i = 0
        while (i < len) {
            val c = text[i]
            when {
                c == '\n' -> { out += GToken(GTokenType.NEWLINE, "\n", i, i + 1); i++ }
                c == '\r' -> i++
                c.isWhitespace() -> i++
                c == '/' && i + 1 < len && text[i + 1] == '/' -> {
                    var j = i + 2
                    while (j < len && text[j] != '\n') j++
                    i = j
                }
                c == '/' && i + 1 < len && text[i + 1] == '*' -> {
                    var j = i + 2
                    while (j + 1 < len && !(text[j] == '*' && text[j + 1] == '/')) j++
                    i = (j + 2).coerceAtMost(len)
                }
                c == '"' || c == '\'' -> {
                    val (tok, next) = readString(text, i, c)
                    out += tok; i = next
                }
                c.isDigit() -> {
                    var j = i + 1
                    while (j < len && (text[j].isLetterOrDigit() || text[j] == '.' || text[j] == '_')) j++
                    out += GToken(GTokenType.NUMBER, text.substring(i, j), i, j); i = j
                }
                isIdentStart(c) -> {
                    var j = i + 1
                    while (j < len && isIdentPart(text[j])) j++
                    out += GToken(GTokenType.IDENT, text.substring(i, j), i, j); i = j
                }
                c == '`' -> { // Kotlin backtick-escaped identifier
                    var j = i + 1
                    while (j < len && text[j] != '`') j++
                    out += GToken(GTokenType.IDENT, text.substring(i + 1, j.coerceAtMost(len)), i, (j + 1).coerceAtMost(len))
                    i = (j + 1).coerceAtMost(len)
                }
                else -> {
                    val type = when (c) {
                        '{' -> GTokenType.LBRACE
                        '}' -> GTokenType.RBRACE
                        '(' -> GTokenType.LPAREN
                        ')' -> GTokenType.RPAREN
                        '[' -> GTokenType.LBRACKET
                        ']' -> GTokenType.RBRACKET
                        '.' -> GTokenType.DOT
                        ',' -> GTokenType.COMMA
                        '=' -> if (i + 1 < len && text[i + 1] == '=') GTokenType.OTHER else GTokenType.EQ
                        else -> GTokenType.OTHER
                    }
                    // `==` collapses to a single OTHER token spanning both chars.
                    val end = if (type == GTokenType.OTHER && c == '=') i + 2 else i + 1
                    out += GToken(type, text.substring(i, end), i, end); i = end
                }
            }
        }
        return out
    }

    private fun readString(text: CharSequence, start: Int, quote: Char): Pair<GToken, Int> {
        val len = text.length
        val triple = quote == '"' && start + 2 < len && text[start + 1] == '"' && text[start + 2] == '"'
        if (triple) {
            var j = start + 3
            while (j + 2 < len && !(text[j] == '"' && text[j + 1] == '"' && text[j + 2] == '"')) j++
            val end = (j + 3).coerceAtMost(len)
            return GToken(GTokenType.STRING, text.subSequence(start, end).toString(), start, end) to end
        }
        var j = start + 1
        while (j < len) {
            val ch = text[j]
            if (ch == '\\') { j += 2; continue }
            if (ch == quote) { j++; break }
            if (ch == '\n') break // unterminated single-line string; stop tolerantly
            j++
        }
        val end = j.coerceAtMost(len)
        return GToken(GTokenType.STRING, text.subSequence(start, end).toString(), start, end) to end
    }

    private fun isIdentStart(c: Char) = c.isLetter() || c == '_' || c == '$'
    private fun isIdentPart(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'

    // ------------------------------------------------------------------------------------------
    // Block / call helpers over a token list.
    // ------------------------------------------------------------------------------------------

    /**
     * Finds the body of the first block named [name] whose opening brace lies within
     * [from, until). Returns the index range of the tokens *strictly inside* the braces, or null.
     * Matches both `name { … }` and `name(args) { … }` (the trailing-lambda form).
     */
    fun findBlockBody(tokens: List<GToken>, name: String, from: Int = 0, until: Int = tokens.size): IntRange? {
        var i = from
        while (i < until) {
            val t = tokens[i]
            if (t.type == GTokenType.IDENT && t.text == name) {
                val brace = indexOfOpeningBrace(tokens, i + 1, until)
                if (brace != null) {
                    val close = matchBrace(tokens, brace, until) ?: return null
                    return (brace + 1) until close
                }
            }
            i++
        }
        return null
    }

    /** From [start], skip over an optional `( … )` argument list and return the index of the next `{`. */
    private fun indexOfOpeningBrace(tokens: List<GToken>, start: Int, until: Int): Int? {
        var i = start
        // Allow a parenthesised argument list before the trailing lambda.
        if (i < until && tokens[i].type == GTokenType.LPAREN) {
            val close = matchParen(tokens, i, until) ?: return null
            i = close + 1
        }
        while (i < until && tokens[i].type == GTokenType.NEWLINE) i++
        return if (i < until && tokens[i].type == GTokenType.LBRACE) i else null
    }

    /** Index of the matching `}` for the `{` at [open], or null if unbalanced. */
    fun matchBrace(tokens: List<GToken>, open: Int, until: Int = tokens.size): Int? {
        var depth = 0
        var i = open
        while (i < until) {
            when (tokens[i].type) {
                GTokenType.LBRACE -> depth++
                GTokenType.RBRACE -> { depth--; if (depth == 0) return i }
                else -> {}
            }
            i++
        }
        return null
    }

    /** Index of the matching `)` for the `(` at [open], or null if unbalanced. */
    fun matchParen(tokens: List<GToken>, open: Int, until: Int = tokens.size): Int? {
        var depth = 0
        var i = open
        while (i < until) {
            when (tokens[i].type) {
                GTokenType.LPAREN -> depth++
                GTokenType.RPAREN -> { depth--; if (depth == 0) return i }
                else -> {}
            }
            i++
        }
        return null
    }

    /** Immediate child block names declared inside [range] (e.g. build-type / flavor names). */
    fun childBlockNames(tokens: List<GToken>, range: IntRange): List<String> {
        val names = ArrayList<String>()
        var i = range.first
        val end = range.last + 1
        while (i < end) {
            val t = tokens[i]
            if (t.type == GTokenType.LBRACE) {
                // Skip nested blocks so we only pick up direct children.
                val close = matchBrace(tokens, i, end) ?: break
                // The name is the last IDENT before this brace on the same statement.
                var k = i - 1
                while (k >= range.first && tokens[k].type == GTokenType.NEWLINE) k--
                // Skip a `create("x")` / `register("x")`-style call's parens if present.
                if (k >= range.first && tokens[k].type == GTokenType.RPAREN) {
                    // Named via create("debug"): grab the string inside.
                    val openP = matchParenBackwards(tokens, k, range.first)
                    if (openP != null) {
                        val str = tokens.subList(openP + 1, k).firstOrNull { it.type == GTokenType.STRING }
                        if (str != null) names += str.stringValue()
                    }
                } else if (k >= range.first && tokens[k].type == GTokenType.IDENT) {
                    names += tokens[k].text
                }
                i = close + 1
            } else i++
        }
        return names.distinct()
    }

    private fun matchParenBackwards(tokens: List<GToken>, close: Int, from: Int): Int? {
        var depth = 0
        var i = close
        while (i >= from) {
            when (tokens[i].type) {
                GTokenType.RPAREN -> depth++
                GTokenType.LPAREN -> { depth--; if (depth == 0) return i }
                else -> {}
            }
            i--
        }
        return null
    }
}
