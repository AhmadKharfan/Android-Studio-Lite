package com.ahmadkharfan.androidstudiolite.feature.terminal.emulator

/**
 * A small, clean-room VT100/xterm-style terminal emulator: it consumes the raw character stream a PTY
 * produces and maintains a character grid with cursor and SGR (color/attribute) state, so interactive
 * and curses programs (`top`, `vi`, progress bars that rewrite a line) render correctly instead of
 * scrolling garbage.
 *
 * This is ASL's own implementation — written from the public ECMA-48 / xterm control-sequence
 * specifications, with no code taken from any GPL terminal emulator (Termux included). It is pure
 * Kotlin with no Android dependencies, so it runs and is unit-tested on the JVM.
 *
 * Scope is deliberately the common subset real TUIs use: CSI cursor motion, erase, insert/delete of
 * lines and characters, scrolling regions, SGR colors/attributes, and the handful of ESC and DEC
 * private-mode sequences those depend on. Anything unrecognised is swallowed rather than printed.
 */
class TerminalEmulator(rows: Int, cols: Int) {

    var rows = rows.coerceAtLeast(1)
        private set
    var cols = cols.coerceAtLeast(1)
        private set

    private var grid: Array<Array<TerminalCell>> = blankGrid(this.rows, this.cols)

    var cursorRow = 0
        private set
    var cursorCol = 0
        private set
    var cursorVisible = true
        private set

    // Current SGR attributes applied to newly written cells.
    private var curFg = DEFAULT_COLOR
    private var curBg = DEFAULT_COLOR
    private var bold = false
    private var underline = false
    private var inverse = false

    // Scrolling region (inclusive), reset to the full screen. Curses apps use this heavily.
    private var scrollTop = 0
    private var scrollBottom = this.rows - 1

    // Saved cursor (DECSC / ESC 7).
    private var savedRow = 0
    private var savedCol = 0

    // "Wrap pending" latch: after writing the last column we stay put until the next glyph, matching
    // xterm — this is what stops a full-width line from eating a blank line.
    private var wrapPending = false

    // Per-row "changed since last snapshot" flags. snapshot() reuses the previous row list for rows
    // that stayed clean so the UI sees referential equality and can skip work.
    private var dirtyRows = BooleanArray(this.rows) { true }
    private var snapshotCache: Array<List<TerminalCell>>? = null

    // Lines that have scrolled off the top of the screen (oldest first), capped at [scrollbackLimit].
    // Populated only for whole-screen scrolls in the normal buffer; alt-screen apps never contribute.
    private val scrollback = ArrayDeque<List<TerminalCell>>()
    private var scrollbackLimit = DEFAULT_SCROLLBACK
    private var scrollbackCache: List<List<TerminalCell>> = emptyList()
    private var scrollbackDirty = true
    private var altScreen = false

    private enum class State { GROUND, ESC, CSI, OSC, CHARSET }

    private var state = State.GROUND
    private val paramBuf = StringBuilder()

    // ---------------------------------------------------------------- public API

    /** Feed a decoded chunk of output. Callers decode PTY bytes as UTF-8 before handing them here. */
    fun feed(text: CharSequence) {
        for (c in text) feed(c)
    }

    fun feed(c: Char) {
        when (state) {
            State.GROUND -> ground(c)
            State.ESC -> esc(c)
            State.CSI -> csi(c)
            State.OSC -> osc(c)
            State.CHARSET -> state = State.GROUND // consume the single charset-designator byte
        }
    }

    /**
     * Immutable snapshot for rendering. Rows unchanged since the previous snapshot keep their old
     * list instance (referential equality) so the renderer can cheaply detect what changed; the
     * scrollback list is likewise only rebuilt when it actually changed.
     */
    fun snapshot(): TerminalScreen {
        val prev = snapshotCache
        val canReuse = prev != null && prev.size == rows
        val out = Array(rows) { r ->
            if (canReuse && !dirtyRows[r]) prev!![r] else grid[r].toList()
        }
        snapshotCache = out
        dirtyRows.fill(false)
        return TerminalScreen(
            rows = rows,
            cols = cols,
            lines = out.asList(),
            cursorRow = cursorRow,
            cursorCol = cursorCol,
            cursorVisible = cursorVisible,
            scrollback = scrollbackSnapshot(),
        )
    }

    private fun scrollbackSnapshot(): List<List<TerminalCell>> {
        if (scrollbackDirty) {
            scrollbackCache = scrollback.toList()
            scrollbackDirty = false
        }
        return scrollbackCache
    }

    private fun markDirty(row: Int) {
        if (row in 0 until rows) dirtyRows[row] = true
    }

    private fun markRegionDirty(top: Int, bottom: Int) {
        for (r in top.coerceAtLeast(0)..bottom.coerceAtMost(rows - 1)) dirtyRows[r] = true
    }

    private fun markAllDirty() = dirtyRows.fill(true)

    /**
     * Resize the grid to [newRows] × [newCols], preserving the top-left content and clamping the cursor
     * and scrolling region. Sent to the PTY via TIOCSWINSZ separately; this keeps the model consistent.
     */
    fun resize(newRows: Int, newCols: Int) {
        val r = newRows.coerceAtLeast(1)
        val c = newCols.coerceAtLeast(1)
        if (r == rows && c == cols) return
        val next = blankGrid(r, c)
        for (row in 0 until minOf(rows, r)) {
            for (col in 0 until minOf(cols, c)) {
                next[row][col] = grid[row][col]
            }
        }
        grid = next
        rows = r
        cols = c
        cursorRow = cursorRow.coerceIn(0, r - 1)
        cursorCol = cursorCol.coerceIn(0, c - 1)
        scrollTop = 0
        scrollBottom = r - 1
        wrapPending = false
        dirtyRows = BooleanArray(r) { true }
        snapshotCache = null
    }

    // ---------------------------------------------------------------- ground state

    private fun ground(c: Char) {
        when (c) {
            '\u001B' -> { state = State.ESC; paramBuf.setLength(0) }
            '\n', '\u000B', '\u000C' -> lineFeed()
            '\r' -> { cursorCol = 0; wrapPending = false }
            '\b' -> { if (cursorCol > 0) cursorCol--; wrapPending = false }
            '\t' -> tab()
            '\u0007' -> Unit // bell
            else -> if (c >= ' ') putChar(c)
        }
    }

    private fun putChar(c: Char) {
        if (wrapPending) {
            cursorCol = 0
            lineFeed()
            wrapPending = false
        }
        grid[cursorRow][cursorCol] = TerminalCell(c, curFg, curBg, bold, underline, inverse)
        markDirty(cursorRow)
        if (cursorCol == cols - 1) {
            wrapPending = true
        } else {
            cursorCol++
        }
    }

    private fun tab() {
        val next = ((cursorCol / 8) + 1) * 8
        cursorCol = next.coerceAtMost(cols - 1)
        wrapPending = false
    }

    private fun lineFeed() {
        if (cursorRow == scrollBottom) {
            scrollUp(1)
        } else if (cursorRow < rows - 1) {
            cursorRow++
        }
        wrapPending = false
    }

    // ---------------------------------------------------------------- ESC state

    private fun esc(c: Char) {
        when (c) {
            '[' -> { state = State.CSI; paramBuf.setLength(0) }
            ']' -> { state = State.OSC; paramBuf.setLength(0) }
            '(', ')', '*', '+' -> state = State.CHARSET
            'M' -> { reverseIndex(); state = State.GROUND }
            '7' -> { savedRow = cursorRow; savedCol = cursorCol; state = State.GROUND }
            '8' -> { cursorRow = savedRow; cursorCol = savedCol; wrapPending = false; state = State.GROUND }
            'c' -> { reset(); state = State.GROUND }
            else -> state = State.GROUND
        }
    }

    private fun reverseIndex() {
        if (cursorRow == scrollTop) scrollDown(1) else if (cursorRow > 0) cursorRow--
        wrapPending = false
    }

    // ---------------------------------------------------------------- OSC (window title etc.)

    private fun osc(c: Char) {
        // Terminated by BEL or ST (ESC \). We don't render titles, so just consume to the terminator.
        if (c == '\u0007') {
            state = State.GROUND
        } else if (c == '\u001B') {
            state = State.ESC // the trailing '\' will drop us back to GROUND via esc()'s else branch
        }
    }

    // ---------------------------------------------------------------- CSI state

    private fun csi(c: Char) {
        if (c in '0'..'9' || c == ';' || c == '?' || c == ':') {
            paramBuf.append(c)
            return
        }
        if (c in ' '..'/') {
            // Intermediate bytes (e.g. space) — keep collecting; rarely used, safe to ignore.
            return
        }
        dispatchCsi(c)
        state = State.GROUND
    }

    private fun dispatchCsi(final: Char) {
        val raw = paramBuf.toString()
        val privateMode = raw.startsWith("?")
        val body = raw.removePrefix("?")
        val params = body.split(';').map { it.toIntOrNull() }
        fun p(index: Int, default: Int): Int = params.getOrNull(index)?.takeIf { it >= 0 } ?: default

        when (final) {
            'A' -> moveCursor(-p(0, 1), 0)
            'B' -> moveCursor(p(0, 1), 0)
            'C' -> moveCursor(0, p(0, 1))
            'D' -> moveCursor(0, -p(0, 1))
            'E' -> { cursorCol = 0; moveCursor(p(0, 1), 0) }
            'F' -> { cursorCol = 0; moveCursor(-p(0, 1), 0) }
            'G', '`' -> setCursor(cursorRow, p(0, 1) - 1)
            'd' -> setCursor(p(0, 1) - 1, cursorCol)
            'H', 'f' -> setCursor(p(0, 1) - 1, p(1, 1) - 1)
            'J' -> eraseDisplay(p(0, 0))
            'K' -> eraseLine(p(0, 0))
            'L' -> insertLines(p(0, 1))
            'M' -> deleteLines(p(0, 1))
            'P' -> deleteChars(p(0, 1))
            'X' -> eraseChars(p(0, 1))
            '@' -> insertBlanks(p(0, 1))
            'S' -> scrollUp(p(0, 1))
            'T' -> scrollDown(p(0, 1))
            'r' -> setScrollRegion(p(0, 1) - 1, p(1, rows) - 1)
            's' -> { savedRow = cursorRow; savedCol = cursorCol }
            'u' -> { cursorRow = savedRow; cursorCol = savedCol; wrapPending = false }
            'm' -> applySgr(params)
            'h' -> if (privateMode) setPrivateMode(params, true)
            'l' -> if (privateMode) setPrivateMode(params, false)
            else -> Unit
        }
    }

    private fun setPrivateMode(params: List<Int?>, enable: Boolean) {
        for (mode in params) {
            when (mode) {
                25 -> cursorVisible = enable // DECTCEM show/hide cursor
                // Enter/leave alt screen: track it (so scroll-back isn't polluted by TUIs) and give
                // the app a clean slate either way.
                1049, 47, 1047 -> { altScreen = enable; clearAll() }
            }
        }
    }

    // ---------------------------------------------------------------- cursor motion

    private fun moveCursor(dRow: Int, dCol: Int) {
        cursorRow = (cursorRow + dRow).coerceIn(0, rows - 1)
        cursorCol = (cursorCol + dCol).coerceIn(0, cols - 1)
        wrapPending = false
    }

    private fun setCursor(row: Int, col: Int) {
        cursorRow = row.coerceIn(0, rows - 1)
        cursorCol = col.coerceIn(0, cols - 1)
        wrapPending = false
    }

    // ---------------------------------------------------------------- erase / edit

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> { eraseLine(0); for (r in cursorRow + 1 until rows) blankRow(r) }
            1 -> { eraseLine(1); for (r in 0 until cursorRow) blankRow(r) }
            else -> clearAll()
        }
    }

    private fun eraseLine(mode: Int) {
        val row = grid[cursorRow]
        val range = when (mode) {
            0 -> cursorCol until cols
            1 -> 0..cursorCol.coerceAtMost(cols - 1)
            else -> 0 until cols
        }
        for (col in range) row[col] = blankCell()
        markDirty(cursorRow)
    }

    private fun eraseChars(n: Int) {
        val row = grid[cursorRow]
        for (col in cursorCol until minOf(cols, cursorCol + n)) row[col] = blankCell()
        markDirty(cursorRow)
    }

    private fun deleteChars(n: Int) {
        val row = grid[cursorRow]
        val count = n.coerceIn(0, cols - cursorCol)
        for (col in cursorCol until cols) {
            row[col] = if (col + count < cols) row[col + count] else blankCell()
        }
        markDirty(cursorRow)
    }

    private fun insertBlanks(n: Int) {
        val row = grid[cursorRow]
        val count = n.coerceIn(0, cols - cursorCol)
        for (col in cols - 1 downTo cursorCol) {
            row[col] = if (col - count >= cursorCol) row[col - count] else blankCell()
        }
        markDirty(cursorRow)
    }

    private fun insertLines(n: Int) {
        if (cursorRow < scrollTop || cursorRow > scrollBottom) return
        repeat(n.coerceAtMost(scrollBottom - cursorRow + 1)) {
            for (r in scrollBottom downTo cursorRow + 1) grid[r] = grid[r - 1]
            grid[cursorRow] = blankRowArray()
        }
        markRegionDirty(cursorRow, scrollBottom)
    }

    private fun deleteLines(n: Int) {
        if (cursorRow < scrollTop || cursorRow > scrollBottom) return
        repeat(n.coerceAtMost(scrollBottom - cursorRow + 1)) {
            for (r in cursorRow until scrollBottom) grid[r] = grid[r + 1]
            grid[scrollBottom] = blankRowArray()
        }
        markRegionDirty(cursorRow, scrollBottom)
    }

    // ---------------------------------------------------------------- scrolling

    private fun scrollUp(n: Int) {
        repeat(n.coerceAtLeast(0)) {
            // A whole-screen scroll in the normal buffer pushes the top line into scroll-back history.
            if (scrollTop == 0 && !altScreen) pushScrollback(grid[0].toList())
            for (r in scrollTop until scrollBottom) grid[r] = grid[r + 1]
            grid[scrollBottom] = blankRowArray()
        }
        markRegionDirty(scrollTop, scrollBottom)
    }

    private fun scrollDown(n: Int) {
        repeat(n.coerceAtLeast(0)) {
            for (r in scrollBottom downTo scrollTop + 1) grid[r] = grid[r - 1]
            grid[scrollTop] = blankRowArray()
        }
        markRegionDirty(scrollTop, scrollBottom)
    }

    private fun pushScrollback(line: List<TerminalCell>) {
        if (scrollbackLimit <= 0) return
        scrollback.addLast(line)
        while (scrollback.size > scrollbackLimit) scrollback.removeFirst()
        scrollbackDirty = true
    }

    private fun setScrollRegion(top: Int, bottom: Int) {
        val t = top.coerceIn(0, rows - 1)
        val b = bottom.coerceIn(0, rows - 1)
        if (t < b) {
            scrollTop = t
            scrollBottom = b
        } else {
            scrollTop = 0
            scrollBottom = rows - 1
        }
        // DECSTBM homes the cursor.
        cursorRow = scrollTop
        cursorCol = 0
        wrapPending = false
    }

    // ---------------------------------------------------------------- SGR

    private fun applySgr(params: List<Int?>) {
        var i = 0
        val list = if (params.isEmpty()) listOf(0) else params.map { it ?: 0 }
        while (i < list.size) {
            when (val code = list[i]) {
                0 -> resetAttributes()
                1 -> bold = true
                22 -> bold = false
                4 -> underline = true
                24 -> underline = false
                7 -> inverse = true
                27 -> inverse = false
                in 30..37 -> curFg = code - 30
                39 -> curFg = DEFAULT_COLOR
                in 40..47 -> curBg = code - 40
                49 -> curBg = DEFAULT_COLOR
                in 90..97 -> curFg = code - 90 + 8
                in 100..107 -> curBg = code - 100 + 8
                38 -> i = extendedColor(list, i) { curFg = it }
                48 -> i = extendedColor(list, i) { curBg = it }
            }
            i++
        }
    }

    /** Handle `38;5;n` (256-color) / `38;2;r;g;b` (truecolor) starting at [start]; returns the index consumed to. */
    private inline fun extendedColor(list: List<Int>, start: Int, set: (Int) -> Unit): Int {
        return when (list.getOrNull(start + 1)) {
            5 -> { list.getOrNull(start + 2)?.let(set); start + 2 }
            2 -> {
                // Pack r,g,b into a palette index space above 255 so the renderer can recover the RGB.
                val r = list.getOrNull(start + 2) ?: 0
                val g = list.getOrNull(start + 3) ?: 0
                val b = list.getOrNull(start + 4) ?: 0
                set(256 + ((r and 0xFF) shl 16) + ((g and 0xFF) shl 8) + (b and 0xFF))
                start + 4
            }
            else -> start
        }
    }

    private fun resetAttributes() {
        curFg = DEFAULT_COLOR
        curBg = DEFAULT_COLOR
        bold = false
        underline = false
        inverse = false
    }

    // ---------------------------------------------------------------- helpers

    private fun reset() {
        resetAttributes()
        clearAll()
        scrollback.clear()
        scrollbackDirty = true
        altScreen = false
        scrollTop = 0
        scrollBottom = rows - 1
        cursorRow = 0
        cursorCol = 0
        cursorVisible = true
        wrapPending = false
    }

    private fun clearAll() {
        for (r in 0 until rows) blankRow(r)
        cursorRow = 0
        cursorCol = 0
        wrapPending = false
    }

    private fun blankRow(r: Int) {
        grid[r] = blankRowArray()
        markDirty(r)
    }

    private fun blankRowArray(): Array<TerminalCell> = Array(cols) { blankCell() }

    private fun blankCell(): TerminalCell =
        if (curBg == DEFAULT_COLOR) TerminalCell.BLANK else TerminalCell(' ', DEFAULT_COLOR, curBg)

    private companion object {
        /** Default number of scrolled-off lines to retain for scroll-back history. */
        const val DEFAULT_SCROLLBACK = 2000

        fun blankGrid(rows: Int, cols: Int): Array<Array<TerminalCell>> =
            Array(rows) { Array(cols) { TerminalCell.BLANK } }
    }
}
