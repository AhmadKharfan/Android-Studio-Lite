package com.ahmadkharfan.androidstudiolite.data.gradle.parse

enum class GTokenType {
    IDENT, STRING, NUMBER,
    LBRACE, RBRACE, LPAREN, RPAREN, LBRACKET, RBRACKET,
    DOT, COMMA, EQ, NEWLINE, OTHER,
}

data class GToken(
    val type: GTokenType,
    val text: String,
    val start: Int,
    val end: Int,
) {
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
                c == '`' -> {
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
            if (ch == '\n') break
            j++
        }
        val end = j.coerceAtMost(len)
        return GToken(GTokenType.STRING, text.subSequence(start, end).toString(), start, end) to end
    }

    private fun isIdentStart(c: Char) = c.isLetter() || c == '_' || c == '$'
    private fun isIdentPart(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'


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

    private fun indexOfOpeningBrace(tokens: List<GToken>, start: Int, until: Int): Int? {
        var i = start

        if (i < until && tokens[i].type == GTokenType.LPAREN) {
            val close = matchParen(tokens, i, until) ?: return null
            i = close + 1
        }
        while (i < until && tokens[i].type == GTokenType.NEWLINE) i++
        return if (i < until && tokens[i].type == GTokenType.LBRACE) i else null
    }

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

    fun childBlockNames(tokens: List<GToken>, range: IntRange): List<String> {
        val names = ArrayList<String>()
        var i = range.first
        val end = range.last + 1
        while (i < end) {
            val t = tokens[i]
            if (t.type == GTokenType.LBRACE) {

                val close = matchBrace(tokens, i, end) ?: break

                var k = i - 1
                while (k >= range.first && tokens[k].type == GTokenType.NEWLINE) k--

                if (k >= range.first && tokens[k].type == GTokenType.RPAREN) {

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
