package com.example.androidstudiolite.feature.editor.engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class EditorSessionTest {
    private fun typeAll(session: EditorSession, text: String) {
        for (c in text) SmartEdit.typeChar(session, c, tabSize = 4)
    }
    @Test
    fun typingRun_coalescesIntoSingleUndo() {
        val s = EditorSession("", EditorLanguage.Kotlin)
        typeAll(s, "hello")
        assertEquals("hello", s.text)
        assertTrue(s.undo())
        assertEquals("", s.text)
        assertFalse(s.canUndo)
        assertTrue(s.redo())
        assertEquals("hello", s.text)
    }
    @Test
    fun newlineBreaksCoalescing() {
        val s = EditorSession("", EditorLanguage.Kotlin)
        typeAll(s, "ab")
        SmartEdit.typeChar(s, '\n', 4)
        typeAll(s, "cd")
        assertEquals("ab\ncd", s.text)
        s.undo()
        assertEquals("ab\n", s.text)
        s.undo()
        assertEquals("ab", s.text)
        s.undo()
        assertEquals("", s.text)
    }
    @Test
    fun backspaceRun_coalesces() {
        val s = EditorSession("hello", EditorLanguage.Kotlin).also { it.setCaret(5) }
        repeat(5) { SmartEdit.backspace(s, 4) }
        assertEquals("", s.text)
        s.undo()
        assertEquals("hello", s.text)
    }
    @Test
    fun undoRestoresCaret() {
        val s = EditorSession("", EditorLanguage.Kotlin)
        typeAll(s, "abc")
        assertEquals(3, s.selection.caret)
        s.undo()
        assertEquals(0, s.selection.caret)
    }
    @Test
    fun incrementalHighlight_propagatesBlockCommentToLaterLine() {
        val s = EditorSession("val a = 1\nval b = 2", EditorLanguage.Kotlin)
        assertTrue(s.tokensForLine(1).any { it.type == TokenType.Keyword })
        s.setCaret(0)
        s.replaceRange(0, 0, "/*")
        val line1 = s.tokensForLine(1)
        assertTrue(line1.isNotEmpty())
        assertTrue(line1.all { it.type == TokenType.Comment })
    }
    @Test
    fun revisionIncrementsPerEdit() {
        val s = EditorSession("", EditorLanguage.Kotlin)
        val before = s.revision
        SmartEdit.typeChar(s, 'x', 4)
        assertEquals(before + 1, s.revision)
    }
}
