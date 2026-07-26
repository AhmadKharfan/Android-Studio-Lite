package com.ahmadkharfan.androidstudiolite.feature.terminal

import androidx.compose.ui.graphics.Color
import com.ahmadkharfan.androidstudiolite.feature.terminal.emulator.DEFAULT_COLOR
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalColorsTest {

    @Test
    fun `default color index preserves supplied color`() {
        val default = Color(0xFF123456)

        assertEquals(default, ansiColor(DEFAULT_COLOR, default))
    }

    @Test
    fun `standard ansi colors preserve current dark and light background variants`() {
        val unusedDefault = Color.Magenta

        assertEquals(Color(0xFFCD3131), ansiColor(1, unusedDefault))
        assertEquals(Color(0xFF6B7280), ansiColor(7, unusedDefault, lightBackground = true))
        assertEquals(Color(0xFF1F2937), ansiColor(15, unusedDefault, lightBackground = true))
        assertEquals(Color(0xFF2472C8), ansiColor(4, unusedDefault, lightBackground = true))
    }

    @Test
    fun `color cube and grayscale indices use current channel mapping`() {
        val unusedDefault = Color.Magenta

        assertEquals(Color(0xFF000000), ansiColor(16, unusedDefault))
        assertEquals(Color(0xFF00FF00), ansiColor(46, unusedDefault))
        assertEquals(Color(0xFF080808), ansiColor(232, unusedDefault))
        assertEquals(Color(0xFFEEEEEE), ansiColor(255, unusedDefault))
    }

    @Test
    fun `direct rgb index preserves low twenty four bits`() {
        assertEquals(Color(0xFF123456), ansiColor(256 + 0x123456, Color.Magenta))
    }
}
