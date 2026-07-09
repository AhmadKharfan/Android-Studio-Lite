package com.example.androidstudiolite.feature.editor.engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class SmartEditTest {
    private fun session(text: String = "", language: EditorLanguage = EditorLanguage.Kotlin, caret: Int = text.length) =
        EditorSession(text, language).also { it.setCaret(caret) }
    @Test
    fun typingOpenBracket_autoClosesAndKeepsCaretInside() {
        val s = session("")
        SmartEdit.typeChar(s, '(', tabSize = 4)
        assertEquals("()", s.text)
        assertEquals(1, s.selection.caret)
    }
    @Test
    fun typingClosingBracket_skipsOverExistingCloser() {
        val s = session("")
        SmartEdit.typeChar(s, '(', 4)
        SmartEdit.typeChar(s, ')', 4)
        assertEquals("()", s.text)
        assertEquals(2, s.selection.caret)
    }
    @Test
    fun typingQuote_autoClosesInCode_butNotInPlainText() {
        val code = session("", EditorLanguage.Kotlin)
        SmartEdit.typeChar(code, '"', 4)
        assertEquals("\"\"", code.text)
        assertEquals(1, code.selection.caret)
        val plain = session("", EditorLanguage.Plain)
        SmartEdit.typeChar(plain, '"', 4)
        assertEquals("\"", plain.text)
    }
    @Test
    fun quoteNotAutoClosedAfterIdentifier() {
        val s = session("dont", EditorLanguage.Kotlin)
        SmartEdit.typeChar(s, '\'', 4)
        assertEquals("dont'", s.text)
    }
    @Test
    fun smartEnter_preservesIndent() {
        val s = session("    val x = 1")
        SmartEdit.typeChar(s, '\n', 4)
        assertEquals("    val x = 1\n    ", s.text)
        assertEquals(s.text.length, s.selection.caret)
    }
    @Test
    fun smartEnter_expandsEmptyBracePair() {
        val s = session("{}", caret = 1)
        SmartEdit.typeChar(s, '\n', 4)
        assertEquals("{\n    \n}", s.text)
        assertEquals(6, s.selection.caret)
    }
    @Test
    fun smartEnter_continuesLineComment() {
        val s = session("// note")
        SmartEdit.typeChar(s, '\n', 4)
        assertEquals("// note\n// ", s.text)
    }
    @Test
    fun backspace_deletesEmptyPair() {
        val s = session("")
        SmartEdit.typeChar(s, '(', 4)
        SmartEdit.backspace(s, 4)
        assertEquals("", s.text)
        assertEquals(0, s.selection.caret)
    }
    @Test
    fun backspace_dedentsToPreviousTabStop() {
        val s = session("      ", caret = 6)
        SmartEdit.backspace(s, 4)
        assertEquals("    ", s.text)
        assertEquals(4, s.selection.caret)
    }
    @Test
    fun xmlTagAutoClosesOnGreaterThan() {
        val s = session("<row", EditorLanguage.Xml, caret = 4)
        SmartEdit.typeChar(s, '>', 4)
        assertEquals("<row></row>", s.text)
        assertEquals(5, s.selection.caret)
    }
    @Test
    fun smartEnter_composableStringExit_insertsNewlineAfterCall() {
        val text = """Text("Hello")"""
        val caret = text.indexOf('"') + 6
        val s = session(text, caret = caret)
        SmartEdit.typeChar(s, '\n', 4)
        assertTrue(s.text.startsWith("""Text("Hello")"""))
        assertTrue(s.text.contains("\n"))
        assertTrue(s.selection.caret > text.length)
    }
    @Test
    fun smartEnter_insideKotlinString_insertsLiteralNewline() {
        val s = session("""val s = "foobar"""")
        s.setCaret(s.text.indexOf("foo") + 3)
        SmartEdit.typeChar(s, '\n', 4)
        assertTrue(s.text.contains("foo\n"))
    }
}
