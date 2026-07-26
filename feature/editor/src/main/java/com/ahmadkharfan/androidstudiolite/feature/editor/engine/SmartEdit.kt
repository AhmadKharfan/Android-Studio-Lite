package com.ahmadkharfan.androidstudiolite.feature.editor.engine
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
        val sel = session.selection
        if (!sel.isCollapsed) {
            typeOverSelection(session, ch)
            return
        }
        typeAtCaret(session, ch, tabSize)
    }

    private fun typeOverSelection(session: EditorSession, ch: Char) {
        val selection = session.selection
        val close = OPEN_TO_CLOSE[ch]
            ?: if (ch in QUOTES && session.language.supportsSmartQuotes) ch else null
        if (close != null) {
            val selected = session.document.substring(selection.start, selection.end)
            session.replaceRange(
                selection.start,
                selection.end,
                "$ch$selected$close",
                caret = selection.start + 1 + selected.length,
            )
        } else {
            session.replaceRange(selection.start, selection.end, ch.toString(), caret = selection.start + 1)
        }
    }

    private fun typeAtCaret(session: EditorSession, ch: Char, tabSize: Int) {
        val doc = session.document
        val pos = session.selection.caret
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
        if (ch in QUOTES && session.language.supportsSmartQuotes && shouldAutoClose(next) && !isIdentChar(prev)) {
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
        val context = smartEnterContext(session, tabSize)
        val edit = enterAfterComposableCall(context)
            ?: enterInsideKotlinString(context)
            ?: enterBetweenPairedDelimiters(context)
            ?: enterInBlockComment(context)
            ?: enterInLineComment(context)
            ?: enterWithIndent(context)
        session.replaceRange(edit.offset, edit.offset, edit.text, caret = edit.caret)
    }

    private fun smartEnterContext(session: EditorSession, tabSize: Int): SmartEnterContext {
        val doc = session.document
        val position = session.selection.caret
        val lineStart = doc.lineStartOffset(doc.lineOfOffset(position))
        val prefix = doc.substring(lineStart, position)
        return SmartEnterContext(
            text = doc.text,
            position = position,
            prefix = prefix,
            indent = prefix.takeWhile { it == ' ' || it == '\t' },
            indentUnit = " ".repeat(tabSize),
            language = session.language,
        )
    }

    private data class SmartEnterContext(
        val text: String,
        val position: Int,
        val prefix: String,
        val indent: String,
        val indentUnit: String,
        val language: EditorLanguage,
    )

    private data class SmartEnterEdit(
        val offset: Int,
        val text: String,
        val caret: Int,
    )

    private fun enterAfterComposableCall(context: SmartEnterContext): SmartEnterEdit? {
        if (context.language != EditorLanguage.Kotlin) return null
        val offset = KotlinLexUtil.composableStringExitInsertOffset(context.text, context.position) ?: return null
        val insert = "\n" + context.indent
        return SmartEnterEdit(offset, insert, offset + insert.length)
    }

    private fun enterInsideKotlinString(context: SmartEnterContext): SmartEnterEdit? {
        if (context.language != EditorLanguage.Kotlin) return null
        if (!KotlinLexUtil.isInsideStringLiteral(context.text, context.position)) return null
        val insert = "\n" + context.indent
        return SmartEnterEdit(context.position, insert, context.position + insert.length)
    }

    private fun enterBetweenPairedDelimiters(context: SmartEnterContext): SmartEnterEdit? {
        val before = context.text.getOrNull(context.position - 1) ?: return null
        val after = context.text.getOrNull(context.position)
        if (before !in OPEN_TO_CLOSE || after != OPEN_TO_CLOSE[before]) return null
        val body = "\n" + context.indent + context.indentUnit
        val insert = body + "\n" + context.indent
        return SmartEnterEdit(context.position, insert, context.position + body.length)
    }

    private fun enterInBlockComment(context: SmartEnterContext): SmartEnterEdit? {
        val trimmed = context.prefix.trim()
        if (!trimmed.startsWith("/*") && !trimmed.startsWith("*")) return null
        val commentPrefix = if (trimmed.startsWith("/*")) " * " else "* "
        val insert = "\n" + context.indent + commentPrefix
        return SmartEnterEdit(context.position, insert, context.position + insert.length)
    }

    private fun enterInLineComment(context: SmartEnterContext): SmartEnterEdit? {
        if (!context.prefix.trim().startsWith("//")) return null
        val insert = "\n" + context.indent + "// "
        return SmartEnterEdit(context.position, insert, context.position + insert.length)
    }

    private fun enterWithIndent(context: SmartEnterContext): SmartEnterEdit {
        val trimmedEnd = context.prefix.trimEnd()
        val lastNonSpace = trimmedEnd.lastOrNull()
        val deeper = (lastNonSpace != null && lastNonSpace in "([{") ||
            (context.language == EditorLanguage.Kotlin && trimmedEnd.endsWith("->"))
        val insert = if (deeper) {
            "\n" + context.indent + context.indentUnit
        } else {
            "\n" + context.indent
        }
        return SmartEnterEdit(context.position, insert, context.position + insert.length)
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
