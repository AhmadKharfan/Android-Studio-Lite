package com.ahmadkharfan.androidstudiolite.feature.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ImeComposeEditTest {

    @Test
    fun `accepting completion then opening paren keeps single identifier`() {
        val session = EditorSession("Tex", EditorLanguage.Kotlin).also { it.setCaret(3) }
        val controller = EditorCompletionController()
        val item = CompletionItem("Text", "Text", CompletionKind.Class)
        controller.accept(session, item)
        SmartEdit.typeChar(session, '(', tabSize = 4)
        assertEquals("Text()", session.text)
    }

    @Test
    fun `composing update replaces typed word instead of appending`() {
        val session = EditorSession("Te", EditorLanguage.Kotlin).also { it.setCaret(2) }
        val start = wordStart(session.text, session.selection.caret)
        session.replaceRange(start, session.selection.caret, "Tex", caret = start + 3)
        assertEquals("Tex", session.text)
    }

    @Test
    fun `backspace then composing update keeps shortened word`() {
        val session = EditorSession("Text", EditorLanguage.Kotlin).also { it.setCaret(4) }
        SmartEdit.backspace(session, tabSize = 4)
        assertEquals("Tex", session.text)
        val start = wordStart(session.text, session.selection.caret)
        session.replaceRange(start, session.selection.caret, "Tex", caret = start + 3)
        assertEquals("Tex", session.text)
    }

    @Test
    fun `ime commit of word plus paren strips duplicate word`() {
        val session = EditorSession("Text", EditorLanguage.Kotlin).also { it.setCaret(4) }
        val imeCommit = "Text("
        val word = "Text"
        val tail = imeCommit.removePrefix(word)
        SmartEdit.typeChar(session, tail.single(), tabSize = 4)
        assertEquals("Text()", session.text)
    }

    @Test
    fun `ime commit of duplicate word alone is no-op`() {
        val session = EditorSession("Text", EditorLanguage.Kotlin).also { it.setCaret(4) }
        val imeCommit = "Text"
        val word = "Text"
        val tail = imeCommit.removePrefix(word)
        assertEquals("", tail)
    }

    private fun wordStart(text: String, caret: Int): Int {
        var start = caret
        while (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '_')) start--
        return start
    }
}
