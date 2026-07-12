package com.ahmadkharfan.androidstudiolite.feature.editor.engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class DiagnosticShiftTest {
    private fun diag(start: Int, end: Int) = Diagnostic(start, end, DiagnosticSeverity.Error, "x")

    @Test
    fun offsetsBeforeEditAreUnchanged() {
        val old = "val x = fooBar"
        val new = "val x = fooBar\nval y = 1"
        val shifted = shiftDiagnostics(listOf(diag(8, 14)), old, new)
        assertEquals(8, shifted.single().start)
        assertEquals(14, shifted.single().end)
    }

    @Test
    fun insertionBeforeShiftsRight() {
        val old = "fooBar"
        val new = "  fooBar"
        val shifted = shiftDiagnostics(listOf(diag(0, 6)), old, new)
        assertEquals(2, shifted.single().start)
        assertEquals(8, shifted.single().end)
    }

    @Test
    fun editInsideStretchesRange() {
        val old = "fooBar"
        val new = "fooXXBar"
        val shifted = shiftDiagnostics(listOf(diag(0, 6)), old, new)
        assertEquals(0, shifted.single().start)
        assertEquals(8, shifted.single().end)
    }

    @Test
    fun deletingWholeRangeDropsDiagnostic() {
        val old = "abcfooBarxyz"
        val new = "abcxyz"
        val shifted = shiftDiagnostics(listOf(diag(3, 9)), old, new)
        assertTrue(shifted.isEmpty())
    }

    @Test
    fun noOpKeepsDiagnostics() {
        val d = listOf(diag(1, 3))
        assertEquals(d, shiftDiagnostics(d, "hello", "hello"))
    }
}
