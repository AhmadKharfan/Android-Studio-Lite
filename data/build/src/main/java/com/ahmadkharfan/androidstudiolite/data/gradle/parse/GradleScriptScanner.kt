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

    fun tokenize(text: CharSequence): List<GToken> = GradleTokenizer(text).run {
        while (hasNext()) readNext()
        tokens
    }

    private class GradleTokenizer(private val text: CharSequence) {
        val tokens = ArrayList<GToken>()
        private var index = 0

        fun hasNext(): Boolean = index < text.length

        fun readNext() {
            val c = text[index]
            when {
                c == '\n' -> addToken(GTokenType.NEWLINE, index + 1)
                c == '\r' || c.isWhitespace() -> index++
                startsWith("//") -> skipLineComment()
                startsWith("/*") -> skipBlockComment()
                c == '"' || c == '\'' -> readString(c)
                c.isDigit() -> readNumber()
                isIdentStart(c) -> readIdentifier()
                c == '`' -> readBacktickIdentifier()
                else -> readSymbol(c)
            }
        }

        private fun startsWith(value: String): Boolean =
            index + value.length <= text.length && text.subSequence(index, index + value.length).toString() == value

        private fun skipLineComment() {
            index += 2
            while (index < text.length && text[index] != '\n') index++
        }

        private fun skipBlockComment() {
            var end = index + 2
            while (end + 1 < text.length && !(text[end] == '*' && text[end + 1] == '/')) end++
            index = (end + 2).coerceAtMost(text.length)
        }

        private fun readString(quote: Char) {
            val start = index
            val triple = quote == '"' && startsWith("\"\"\"")
            val end = if (triple) tripleStringEnd(start) else quotedStringEnd(start, quote)
            addToken(GTokenType.STRING, end)
        }

        private fun tripleStringEnd(start: Int): Int {
            var end = start + 3
            while (end + 2 < text.length && text.subSequence(end, end + 3).toString() != "\"\"\"") end++
            return (end + 3).coerceAtMost(text.length)
        }

        private fun quotedStringEnd(start: Int, quote: Char): Int {
            var end = start + 1
            while (end < text.length) {
                val c = text[end]
                if (c == '\\') {
                    end += 2
                    continue
                }
                if (c == quote) return end + 1
                if (c == '\n') return end
                end++
            }
            return end.coerceAtMost(text.length)
        }

        private fun readNumber() {
            var end = index + 1
            while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '.' || text[end] == '_')) end++
            addToken(GTokenType.NUMBER, end)
        }

        private fun readIdentifier() {
            var end = index + 1
            while (end < text.length && isIdentPart(text[end])) end++
            addToken(GTokenType.IDENT, end)
        }

        private fun readBacktickIdentifier() {
            val start = index
            var end = start + 1
            while (end < text.length && text[end] != '`') end++
            val tokenEnd = (end + 1).coerceAtMost(text.length)
            tokens += GToken(GTokenType.IDENT, text.substring(start + 1, end.coerceAtMost(text.length)), start, tokenEnd)
            index = tokenEnd
        }

        private fun readSymbol(c: Char) {
            val type = when (c) {
                '{' -> GTokenType.LBRACE
                '}' -> GTokenType.RBRACE
                '(' -> GTokenType.LPAREN
                ')' -> GTokenType.RPAREN
                '[' -> GTokenType.LBRACKET
                ']' -> GTokenType.RBRACKET
                '.' -> GTokenType.DOT
                ',' -> GTokenType.COMMA
                '=' -> if (startsWith("==")) GTokenType.OTHER else GTokenType.EQ
                else -> GTokenType.OTHER
            }
            val end = if (type == GTokenType.OTHER && c == '=') index + 2 else index + 1
            addToken(type, end)
        }

        private fun addToken(type: GTokenType, end: Int) {
            tokens += GToken(type, text.substring(index, end), index, end)
            index = end
        }
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

    fun childBlockNames(tokens: List<GToken>, range: IntRange): List<String> =
        childBlocks(tokens, range).map { it.name }.distinct()

    data class ChildBlock(val name: String, val body: IntRange)

    fun childBlocks(tokens: List<GToken>, range: IntRange): List<ChildBlock> {
        val blocks = ArrayList<ChildBlock>()
        var i = range.first
        val end = range.last + 1
        while (i < end) {
            val t = tokens[i]
            if (t.type == GTokenType.LBRACE) {

                val close = matchBrace(tokens, i, end) ?: break

                var k = i - 1
                while (k >= range.first && tokens[k].type == GTokenType.NEWLINE) k--

                val name = when {
                    k >= range.first && tokens[k].type == GTokenType.RPAREN -> {
                        val openP = matchParenBackwards(tokens, k, range.first)
                        openP?.let { tokens.subList(it + 1, k).firstOrNull { tk -> tk.type == GTokenType.STRING } }
                            ?.stringValue()
                    }
                    k >= range.first && tokens[k].type == GTokenType.IDENT -> tokens[k].text
                    else -> null
                }
                if (name != null) blocks += ChildBlock(name, (i + 1) until close)
                i = close + 1
            } else i++
        }
        return blocks
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
