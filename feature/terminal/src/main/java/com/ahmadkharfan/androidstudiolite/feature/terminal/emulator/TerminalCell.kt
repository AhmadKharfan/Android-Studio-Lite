package com.ahmadkharfan.androidstudiolite.feature.terminal.emulator

const val DEFAULT_COLOR = -1

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

data class TerminalScreen(
    val rows: Int,
    val cols: Int,
    val lines: List<List<TerminalCell>>,
    val cursorRow: Int,
    val cursorCol: Int,
    val cursorVisible: Boolean,
    val scrollback: List<List<TerminalCell>> = emptyList(),
) {
    companion object {
        fun blank(rows: Int, cols: Int): TerminalScreen = TerminalScreen(
            rows = rows,
            cols = cols,
            lines = List(rows) { List(cols) { TerminalCell.BLANK } },
            cursorRow = 0,
            cursorCol = 0,
            cursorVisible = true,
            scrollback = emptyList(),
        )
    }
}
