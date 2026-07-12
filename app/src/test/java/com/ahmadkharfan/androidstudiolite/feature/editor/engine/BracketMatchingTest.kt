package com.ahmadkharfan.androidstudiolite.feature.editor.engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
class BracketMatchingTest {
    @Test
    fun matchesForwardFromOpener() {
        val doc = EditorDocument("foo(bar)")
        assertEquals(3 to 7, bracketMatchAt(doc, 4))
    }
    @Test
    fun matchesBackwardFromCloser() {
        val doc = EditorDocument("foo(bar)")
        assertEquals(7 to 3, bracketMatchAt(doc, 8))
    }
    @Test
    fun respectsNesting() {
        val doc = EditorDocument("a(b(c)d)")
        assertEquals(1 to 7, bracketMatchAt(doc, 2))
        assertEquals(3 to 5, bracketMatchAt(doc, 4))
    }
    @Test
    fun mixedBracketTypes() {
        val doc = EditorDocument("x[y]{z}")
        assertEquals(1 to 3, bracketMatchAt(doc, 2))
        assertEquals(4 to 6, bracketMatchAt(doc, 5))
    }
    @Test
    fun nullWhenNotAdjacentToBracket() {
        val doc = EditorDocument("hello")
        assertNull(bracketMatchAt(doc, 2))
    }
    @Test
    fun nullForUnmatchedBracket() {
        val doc = EditorDocument("foo(bar")
        assertNull(bracketMatchAt(doc, 4))
    }
}
