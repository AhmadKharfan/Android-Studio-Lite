package com.example.androidstudiolite.feature.editor.engine
import org.junit.Assert.assertEquals
import org.junit.Test
class EditorDocumentTest {
    @Test
    fun lineIndex_onInitialText() {
        val doc = EditorDocument("abc\ndef\nghi")
        assertEquals(3, doc.lineCount)
        assertEquals(0, doc.lineStartOffset(0))
        assertEquals(4, doc.lineStartOffset(1))
        assertEquals(8, doc.lineStartOffset(2))
        assertEquals("def", doc.lineText(1))
    }
    @Test
    fun offsetPositionRoundTrip() {
        val doc = EditorDocument("abc\ndef\nghi")
        assertEquals(TextPosition(1, 1), doc.offsetToPosition(5))
        assertEquals(9, doc.positionToOffset(2, 1))
        assertEquals(TextPosition(0, 0), doc.offsetToPosition(0))
    }
    @Test
    fun insertWithinLine_keepsLineCount_shiftsFollowing() {
        val doc = EditorDocument("abc\ndef")
        doc.replaceRange(3, 3, "XY")
        assertEquals("abcXY\ndef", doc.text)
        assertEquals(2, doc.lineCount)
        assertEquals(6, doc.lineStartOffset(1))
        assertEquals("def", doc.lineText(1))
    }
    @Test
    fun insertNewlines_growsLineIndex() {
        val doc = EditorDocument("abc")
        doc.replaceRange(0, 0, "x\ny\n")
        assertEquals("x\ny\nabc", doc.text)
        assertEquals(3, doc.lineCount)
        assertEquals(0, doc.lineStartOffset(0))
        assertEquals(2, doc.lineStartOffset(1))
        assertEquals(4, doc.lineStartOffset(2))
        assertEquals("abc", doc.lineText(2))
    }
    @Test
    fun deleteNewline_mergesLines() {
        val doc = EditorDocument("abc\ndef\nghi")
        doc.replaceRange(3, 4, "")
        assertEquals("abcdef\nghi", doc.text)
        assertEquals(2, doc.lineCount)
        assertEquals(7, doc.lineStartOffset(1))
        assertEquals("abcdef", doc.lineText(0))
    }
    @Test
    fun replaceMultiLineRange_updatesIndex() {
        val doc = EditorDocument("one\ntwo\nthree\nfour")
        val start = doc.positionToOffset(1, 0)
        val end = doc.positionToOffset(2, 5)
        doc.replaceRange(start, end, "X")
        assertEquals("one\nX\nfour", doc.text)
        assertEquals(3, doc.lineCount)
        assertEquals("X", doc.lineText(1))
        assertEquals("four", doc.lineText(2))
    }
}
