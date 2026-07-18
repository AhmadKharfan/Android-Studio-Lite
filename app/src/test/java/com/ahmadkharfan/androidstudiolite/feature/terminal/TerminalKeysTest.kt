package com.ahmadkharfan.androidstudiolite.feature.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TerminalKeysTest {

    @Test
    fun arrows_map_to_csi_sequences() {
        assertEquals("\u001B[A", TerminalKeys.bytes(TerminalKey.ArrowUp))
        assertEquals("\u001B[D", TerminalKeys.bytes(TerminalKey.ArrowLeft))
    }

    @Test
    fun control_keys_map_to_control_bytes() {
        assertEquals("\u0003", TerminalKeys.bytes(TerminalKey.CtrlC))
        assertEquals("\r", TerminalKeys.bytes(TerminalKey.Enter))
        assertEquals("\u007F", TerminalKeys.bytes(TerminalKey.Backspace))
        assertEquals("\u001B[3~", TerminalKeys.bytes(TerminalKey.Delete))
        assertEquals("\u001B", TerminalKeys.bytes(TerminalKey.Escape))
    }

    @Test
    fun extra_keys_route_to_special_or_text() {
        assertEquals(TerminalKey.ArrowUp, TerminalKeys.specialForExtraKey("↑"))
        assertEquals(TerminalKey.CtrlC, TerminalKeys.specialForExtraKey("Ctrl+C"))
        assertNull(TerminalKeys.specialForExtraKey("|"))
        assertEquals("|", TerminalKeys.textForExtraKey("|"))
        assertNull(TerminalKeys.textForExtraKey("Esc"))
    }
}
