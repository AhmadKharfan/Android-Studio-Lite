package com.example.androidstudiolite.feature.editor.engine
object SmartEdit {
    private val OPEN_TO_CLOSE = mapOf('(' to ')', '[' to ']', '{' to '}')
    private val CLOSE_TO_OPEN = OPEN_TO_CLOSE.entries.associate { (open, close) -> close to open }
    private val QUOTES = setOf('"', '\'')
    fun type(session: EditorSession, input: String, tabSize: Int) {
        if (input.length == 1) {
            typeChar(session, input[0], tabSize)
        } else {
            val sel = session.selection
            session.replaceRange(sel.start, sel.end, input)
        }
    }
    fun typeChar(session: EditorSession, ch: Char, tabSize: Int) {
        val doc = session.document
        val sel = session.selection
        if (!sel.isCollapsed) {
            val close = OPEN_TO_CLOSE[ch] ?: if (ch in QUOTES && session.language != EditorLanguage.Plain) ch else null
            if (close != null) {
                val selected = doc.substring(sel.start, sel.end)
                session.replaceRange(sel.start, sel.end, "$ch$selected$close", caret = sel.start + 1 + selected.length)
            } else {
                session.replaceRange(sel.start, sel.end, ch.toString(), caret = sel.start + 1)
            }
            return
        }
        val pos = sel.caret
        val next = if (pos < doc.length) doc.charAt(pos) else null
        val prev = if (pos > 0) doc.charAt(pos - 1) else null
        if ((ch in CLOSE_TO_OPEN || ch in QUOTES) && next == ch) {
            session.setCaret(pos + 1)
            session.onChange?.invoke()
            return
        }
        if (ch == '\n') {
            smartEnter(session, tabSize)
            return
        }
        if (ch in OPEN_TO_CLOSE && shouldAutoClose(next)) {
            session.replaceRange(pos, pos, "$ch${OPEN_TO_CLOSE.getValue(ch)}", caret = pos + 1)
            return
        }
        if (ch in QUOTES && session.language != EditorLanguage.Plain && shouldAutoClose(next) && !isIdentChar(prev)) {
            session.replaceRange(pos, pos, "$ch$ch", caret = pos + 1)
            return
        }
        if (ch == '>' && session.language == EditorLanguage.Xml) {
            val tag = xmlTagToClose(doc, pos)
            if (tag != null) {
                session.replaceRange(pos, pos, "></$tag>", caret = pos + 1)
                return
            }
        }
        session.replaceRange(pos, pos, ch.toString(), caret = pos + 1, coalesce = true)
    }
    fun smartEnter(session: EditorSession, tabSize: Int) {
        val doc = session.document
        val text = doc.text
        val pos = session.selection.caret
        val line = doc.lineOfOffset(pos)
        val lineStart = doc.lineStartOffset(line)
        val prefix = doc.substring(lineStart, pos)
        val indent = prefix.takeWhile { it == ' ' || it == '\t' }
        val unit = " ".repeat(tabSize)
        if (session.language == EditorLanguage.Kotlin) {
            KotlinLexUtil.composableStringExitInsertOffset(text, pos)?.let { afterParen ->
                val insert = "\n" + indent
                session.replaceRange(afterParen, afterParen, insert, caret = afterParen + insert.length)
                return
            }
            if (KotlinLexUtil.isInsideStringLiteral(text, pos)) {
                val insert = "\n" + indent
                session.replaceRange(pos, pos, insert, caret = pos + insert.length)
                return
            }
        }
        val before = if (pos > 0) doc.charAt(pos - 1) else null
        val after = if (pos < doc.length) doc.charAt(pos) else null
        if (before != null && before in OPEN_TO_CLOSE && after == OPEN_TO_CLOSE[before]) {
            val body = "\n" + indent + unit
            val insert = body + "\n" + indent
            session.replaceRange(pos, pos, insert, caret = pos + body.length)
            return
        }
        val trimmed = prefix.trim()
        if (trimmed.startsWith("/*") || trimmed.startsWith("*")) {
            val commentPrefix = if (trimmed.startsWith("/*")) " * " else "* "
            val insert = "\n" + indent + commentPrefix
            session.replaceRange(pos, pos, insert, caret = pos + insert.length)
            return
        }
        if (trimmed.startsWith("//")) {
            val insert = "\n" + indent + "// "
            session.replaceRange(pos, pos, insert, caret = pos + insert.length)
            return
        }
        val lastNonSpace = prefix.trimEnd().lastOrNull()
        val deeper = (lastNonSpace != null && lastNonSpace in "([{") ||
            (session.language == EditorLanguage.Kotlin && prefix.trimEnd().endsWith("->"))
        val insert = if (deeper) "\n" + indent + unit else "\n" + indent
        session.replaceRange(pos, pos, insert, caret = pos + insert.length)
    }
    fun backspace(session: EditorSession, tabSize: Int) {
        val doc = session.document
        val sel = session.selection
        if (!sel.isCollapsed) {
            session.replaceRange(sel.start, sel.end, "", caret = sel.start)
            return
        }
        val pos = sel.caret
        if (pos <= 0) return
        val before = doc.charAt(pos - 1)
        val after = if (pos < doc.length) doc.charAt(pos) else null
        val emptyPair = (before in OPEN_TO_CLOSE && after == OPEN_TO_CLOSE[before]) ||
            (before in QUOTES && after == before)
        if (emptyPair) {
            session.replaceRange(pos - 1, pos + 1, "", caret = pos - 1)
            return
        }
        val lineStart = doc.lineStartOffset(doc.lineOfOffset(pos))
        val leading = doc.substring(lineStart, pos)
        if (leading.isNotEmpty() && leading.all { it == ' ' }) {
            val removeCount = ((leading.length - 1) % tabSize) + 1
            session.replaceRange(pos - removeCount, pos, "", caret = pos - removeCount)
            return
        }
        session.replaceRange(pos - 1, pos, "", caret = pos - 1, coalesce = true)
    }
    private fun shouldAutoClose(next: Char?): Boolean =
        next == null || next.isWhitespace() || next in ")]},;"
    private fun isIdentChar(c: Char?): Boolean = c != null && (c.isLetterOrDigit() || c == '_')
    private fun xmlTagToClose(doc: EditorDocument, pos: Int): String? {
        var i = pos - 1
        while (i >= 0) {
            val c = doc.charAt(i)
            if (c == '>') return null
            if (c == '<') break
            i--
        }
        if (i < 0) return null
        var j = i + 1
        if (j >= pos) return null
        val firstChar = doc.charAt(j)
        if (firstChar == '/' || firstChar == '!' || firstChar == '?') return null
        if (pos > 0 && doc.charAt(pos - 1) == '/') return null
        val name = StringBuilder()
        while (j < pos) {
            val c = doc.charAt(j)
            if (c.isLetterOrDigit() || c == '_' || c == '-' || c == '.' || c == ':') name.append(c) else break
            j++
        }
        return name.toString().ifEmpty { null }
    }
}
