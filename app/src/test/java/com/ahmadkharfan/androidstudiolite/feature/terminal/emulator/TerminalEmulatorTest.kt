package com.ahmadkharfan.androidstudiolite.feature.terminal.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalEmulatorTest {

    private val ESC = "\u001B"

    private fun TerminalEmulator.rowText(row: Int): String =
        snapshot().lines[row].joinToString("") { it.char.toString() }.trimEnd()

    @Test
    fun writes_plain_text_and_advances_cursor() {
        val term = TerminalEmulator(4, 20)
        term.feed("hello")
        assertEquals("hello", term.rowText(0))
        assertEquals(0, term.cursorRow)
        assertEquals(5, term.cursorCol)
    }

    @Test
    fun newline_and_carriage_return_move_cursor() {
        val term = TerminalEmulator(4, 20)
        term.feed("ab\r\ncd")
        assertEquals("ab", term.rowText(0))
        assertEquals("cd", term.rowText(1))
        assertEquals(1, term.cursorRow)
    }

    @Test
    fun wraps_at_last_column() {
        val term = TerminalEmulator(4, 3)
        term.feed("abcd")
        assertEquals("abc", term.rowText(0))
        assertEquals("d", term.rowText(1))
    }

    @Test
    fun cursor_positioning_overwrites_in_place() {
        val term = TerminalEmulator(4, 20)
        // A curses-style repaint: home the cursor and overwrite — this is how top rewrites its screen.
        term.feed("first line")
        term.feed("${ESC}[H") // cursor home
        term.feed("SECOND")
        assertEquals("SECONDline", term.rowText(0))
        assertEquals(0, term.cursorRow)
        assertEquals(6, term.cursorCol)
    }

    @Test
    fun erase_display_clears_screen() {
        val term = TerminalEmulator(4, 20)
        term.feed("line one\r\nline two")
        term.feed("${ESC}[2J") // erase whole display
        assertEquals("", term.rowText(0))
        assertEquals("", term.rowText(1))
    }

    @Test
    fun erase_to_end_of_line() {
        val term = TerminalEmulator(4, 20)
        term.feed("abcdef")
        term.feed("${ESC}[H") // home
        term.feed("${ESC}[3C") // right 3 -> col 3
        term.feed("${ESC}[K") // erase to end of line
        assertEquals("abc", term.rowText(0))
    }

    @Test
    fun scrolls_when_output_exceeds_height() {
        val term = TerminalEmulator(3, 10)
        term.feed("l1\r\nl2\r\nl3\r\nl4")
        // First line scrolled off; l2..l4 remain.
        assertEquals("l2", term.rowText(0))
        assertEquals("l3", term.rowText(1))
        assertEquals("l4", term.rowText(2))
    }

    @Test
    fun sgr_sets_colors_and_reset_clears_them() {
        val term = TerminalEmulator(2, 20)
        term.feed("${ESC}[31mRED${ESC}[0mX")
        val row = term.snapshot().lines[0]
        assertEquals(1, row[0].fg) // ANSI red = index 1
        assertTrue(row[0].char == 'R')
        assertEquals(DEFAULT_COLOR, row[3].fg) // after reset
        assertEquals('X', row[3].char)
    }

    @Test
    fun hides_and_shows_cursor_via_dec_private_mode() {
        val term = TerminalEmulator(2, 10)
        term.feed("${ESC}[?25l")
        assertFalse(term.snapshot().cursorVisible)
        term.feed("${ESC}[?25h")
        assertTrue(term.snapshot().cursorVisible)
    }

    @Test
    fun resize_preserves_content_and_clamps_cursor() {
        val term = TerminalEmulator(4, 20)
        term.feed("keep me")
        term.feed("${ESC}[4;10H") // move cursor near the old bottom-right
        term.resize(2, 20)
        assertEquals("keep me", term.rowText(0))
        assertEquals(2, term.snapshot().rows)
        assertTrue(term.cursorRow <= 1)
    }

    @Test
    fun scroll_region_confines_scrolling() {
        val term = TerminalEmulator(5, 10)
        term.feed("${ESC}[2;4r") // scroll region rows 2..4 (1-based)
        // Cursor is homed to the region top (row index 1). Fill the region and force a scroll.
        term.feed("a\r\n")
        term.feed("${ESC}[3;1Hb\r\n")
        term.feed("${ESC}[4;1Hc\r\n") // linefeed at region bottom scrolls region up
        // Row 0 (outside region) stays blank; region scrolled.
        assertEquals("", term.rowText(0))
    }

    @Test
    fun delete_and_insert_characters() {
        val term = TerminalEmulator(2, 20)
        term.feed("abcdef")
        term.feed("${ESC}[H")
        term.feed("${ESC}[2P") // delete 2 chars at start
        assertEquals("cdef", term.rowText(0))
        term.feed("${ESC}[H")
        term.feed("${ESC}[2@") // insert 2 blanks
        assertEquals("  cdef", term.rowText(0))
    }

    @Test
    fun snapshot_reuses_unchanged_row_instances() {
        val term = TerminalEmulator(3, 10)
        term.feed("a\r\nb")
        val first = term.snapshot()
        term.feed("c") // extends row 1 only; row 0 is untouched
        val second = term.snapshot()
        // Clean rows keep their previous list instance so the renderer can skip them.
        assertSame(first.lines[0], second.lines[0])
        assertNotSame(first.lines[1], second.lines[1])
        assertEquals("bc", second.lines[1].joinToString("") { it.char.toString() }.trimEnd())
    }

    @Test
    fun lines_scrolled_off_the_top_go_to_scrollback() {
        val term = TerminalEmulator(2, 10)
        term.feed("l1\r\nl2\r\nl3") // only 2 rows tall, so l1 scrolls off
        val snap = term.snapshot()
        assertEquals(1, snap.scrollback.size)
        assertEquals("l1", snap.scrollback[0].joinToString("") { it.char.toString() }.trimEnd())
        assertEquals("l2", snap.lines[0].joinToString("") { it.char.toString() }.trimEnd())
        assertEquals("l3", snap.lines[1].joinToString("") { it.char.toString() }.trimEnd())
    }

    @Test
    fun alt_screen_does_not_pollute_scrollback() {
        val term = TerminalEmulator(2, 10)
        term.feed("${ESC}[?1049h") // enter alt screen (e.g. a TUI app)
        term.feed("x\r\ny\r\nz") // scrolls within the alt buffer
        assertTrue(term.snapshot().scrollback.isEmpty())
    }
}
