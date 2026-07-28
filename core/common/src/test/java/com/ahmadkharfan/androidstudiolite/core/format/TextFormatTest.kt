package com.ahmadkharfan.androidstudiolite.core.format

import org.junit.Assert.assertEquals
import org.junit.Test

class TextFormatTest {

    @Test
    fun `keeps strings at or below the limit untouched`() {
        assertEquals("short/path.kt", "short/path.kt".middleEllipsis(48))
        assertEquals("abcde", "abcde".middleEllipsis(5))
    }

    @Test
    fun `elides the middle and keeps both ends`() {
        val result = "app/src/main/java/com/example/deeply/nested/Thing.kt".middleEllipsis(20)

        assertEquals(20, result.length)
        assertEquals("app/src/m…d/Thing.kt", result)
    }

    @Test
    fun `result never exceeds the requested length`() {
        val long = "x".repeat(200)

        for (max in 2..60) {
            assertEquals(max, long.middleEllipsis(max).length)
        }
    }

    @Test
    fun `splits evenly on odd budgets and favours the tail on even ones`() {
        assertEquals("abc…hij", "abcdefghij".middleEllipsis(7))
        assertEquals("abc…ghij", "abcdefghij".middleEllipsis(8))
    }
}
