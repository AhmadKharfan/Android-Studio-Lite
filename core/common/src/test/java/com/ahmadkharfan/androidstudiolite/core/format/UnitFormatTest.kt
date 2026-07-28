package com.ahmadkharfan.androidstudiolite.core.format

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitFormatTest {

    @Test
    fun `reports plain bytes below one kilobyte`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1023 B", formatBytes(1023))
    }

    @Test
    fun `switches to kilobytes and megabytes at the binary boundaries`() {
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1023.9 KB", formatBytes(1024L * 1024L - 100))
        assertEquals("1.0 MB", formatBytes(1024L * 1024L))
        assertEquals("2.5 MB", formatBytes(1024L * 1024L * 5 / 2))
    }

    @Test
    fun `keeps growing in megabytes rather than adding a gigabyte unit`() {
        assertEquals("2048.0 MB", formatBytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun `formats a negative size as plain bytes`() {
        assertEquals("-1 B", formatBytes(-1))
    }

    @Test
    fun `formats milliseconds as one decimal of seconds`() {
        assertEquals("0.0s", formatSeconds(0))
        assertEquals("1.5s", formatSeconds(1500))
        assertEquals("12.3s", formatSeconds(12_345))
    }

    @Test
    fun `uses a dot decimal separator regardless of default locale`() {
        val previous = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.GERMANY)
        try {
            assertEquals("1.5s", formatSeconds(1500))
            assertEquals("1.0 MB", formatBytes(1024L * 1024L))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }
}
