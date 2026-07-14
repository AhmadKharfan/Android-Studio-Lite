package com.ahmadkharfan.androidstudiolite.feature.terminal.emulator

/** Sentinel meaning "use the renderer's default foreground/background", i.e. no explicit SGR color. */
const val DEFAULT_COLOR = -1

/**
 * One character cell on the terminal grid: the glyph plus the SGR attributes in force when it was
 * written. Colors are stored as ANSI palette indices (0–15 standard/bright, 16–255 for the extended
 * 256-color set) or [DEFAULT_COLOR]; the renderer resolves indices to real RGB from the active theme.
 */
data class TerminalCell(
    val char: Char = ' ',
    val fg: Int = DEFAULT_COLOR,
    val bg: Int = DEFAULT_COLOR,
    val bold: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false,
) {
    companion object {
        val BLANK = TerminalCell()
    }
}

/**
 * An immutable snapshot of the emulator's visible screen, safe to hand to the UI thread. [lines] is
 * row-major and always [rows] × [cols].
 */
data class TerminalScreen(
    val rows: Int,
    val cols: Int,
    val lines: List<List<TerminalCell>>,
    val cursorRow: Int,
    val cursorCol: Int,
    val cursorVisible: Boolean,
) {
    companion object {
        fun blank(rows: Int, cols: Int): TerminalScreen = TerminalScreen(
            rows = rows,
            cols = cols,
            lines = List(rows) { List(cols) { TerminalCell.BLANK } },
            cursorRow = 0,
            cursorCol = 0,
            cursorVisible = true,
        )
    }
}
