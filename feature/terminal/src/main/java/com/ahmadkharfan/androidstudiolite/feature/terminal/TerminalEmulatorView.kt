package com.ahmadkharfan.androidstudiolite.feature.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.ahmadkharfan.androidstudiolite.designsystem.R
import com.ahmadkharfan.androidstudiolite.feature.terminal.emulator.DEFAULT_COLOR
import com.ahmadkharfan.androidstudiolite.feature.terminal.emulator.TerminalCell
import com.ahmadkharfan.androidstudiolite.feature.terminal.emulator.TerminalScreen
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val FONT_SP = 13f
private const val LINE_HEIGHT_RATIO = 1.30f
private const val AUTOSCROLL_INTERVAL_MS = 40L

private data class TerminalSelection(
    val anchorRow: Int,
    val anchorCol: Int,
    val focusRow: Int,
    val focusCol: Int,
) {
    fun ordered(): SelectionBounds {
        val startFirst = anchorRow < focusRow || (anchorRow == focusRow && anchorCol <= focusCol)
        return if (startFirst) {
            SelectionBounds(anchorRow, anchorCol, focusRow, focusCol)
        } else {
            SelectionBounds(focusRow, focusCol, anchorRow, anchorCol)
        }
    }
}

private data class SelectionBounds(val startRow: Int, val startCol: Int, val endRow: Int, val endCol: Int)

@Composable
fun TerminalEmulatorView(
    screen: TerminalScreen,
    background: Color,
    foreground: Color,
    cursorColor: Color,
    onKey: (String) -> Unit,
    onSpecialKey: (TerminalKey) -> Unit,
    onResize: (rows: Int, cols: Int) -> Unit,
    modifier: Modifier = Modifier,
    enableVolumeKeys: Boolean = true,
    requestKeyboardOnAttach: Boolean = true,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val typeface = remember { ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE }

    val textSizePx = with(density) { FONT_SP.sp.toPx() }
    val paint = remember(typeface, textSizePx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = textSizePx
            isSubpixelText = true
        }
    }
    val charWidthPx = remember(paint) { paint.measureText("M") }
    val lineHeightPx = textSizePx * LINE_HEIGHT_RATIO
    val fm = remember(paint) { paint.fontMetrics }
    val glyphBuilder = remember { StringBuilder(256) }
    val handleRadiusPx = with(density) { 7.dp.toPx() }
    val handleTouchRadiusPx = with(density) { 24.dp.toPx() }


    val screenState = rememberUpdatedState(screen)


    var scrollOffset by remember { mutableFloatStateOf(0f) }

    var indicatorRow by remember { mutableIntStateOf(0) }
    var indicatorCol by remember { mutableIntStateOf(0) }
    var isBrowsing by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<TerminalSelection?>(null) }
    var selecting by remember { mutableStateOf(false) }

    var selAnchorPx by remember { mutableStateOf<Offset?>(null) }
    var pastePos by remember { mutableStateOf<Offset?>(null) }
    var inputHost by remember { mutableStateOf<TerminalInputEditText?>(null) }

    val maxOffset = screen.scrollback.size
    LaunchedEffect(maxOffset) {
        if (scrollOffset > maxOffset) scrollOffset = maxOffset.toFloat()
    }


    LaunchedEffect(screen.cursorRow, screen.cursorCol, scrollOffset, isBrowsing) {
        if (!isBrowsing && scrollOffset == 0f) {
            indicatorRow = screen.cursorRow.coerceIn(0, (screen.rows - 1).coerceAtLeast(0))
            indicatorCol = screen.cursorCol.coerceIn(0, (screen.cols - 1).coerceAtLeast(0))
        }
    }

    fun resetToLive() {
        scrollOffset = 0f
        isBrowsing = false
        indicatorRow = screen.cursorRow.coerceIn(0, (screen.rows - 1).coerceAtLeast(0))
        indicatorCol = screen.cursorCol.coerceIn(0, (screen.cols - 1).coerceAtLeast(0))
    }

    fun moveIndicator(volumeUp: Boolean) {
        val s = screenState.value
        if (!isBrowsing && scrollOffset == 0f) {
            indicatorRow = s.cursorRow.coerceIn(0, (s.rows - 1).coerceAtLeast(0))
            indicatorCol = s.cursorCol.coerceIn(0, (s.cols - 1).coerceAtLeast(0))
        }
        isBrowsing = true
        val lastRow = (s.rows - 1).coerceAtLeast(0)
        val lastCol = (s.cols - 1).coerceAtLeast(0)
        val historyMax = s.scrollback.size
        if (volumeUp) {
            when {
                indicatorCol > 0 -> indicatorCol--
                indicatorRow > 0 -> {
                    indicatorRow--
                    indicatorCol = lastCol
                }
                scrollOffset > 0f -> {
                    scrollOffset = (scrollOffset - 1f).coerceAtLeast(0f)
                    indicatorCol = lastCol
                    if (scrollOffset == 0f) resetToLive()
                }
            }
        } else {
            when {
                indicatorCol < lastCol -> indicatorCol++
                indicatorRow < lastRow -> {
                    indicatorRow++
                    indicatorCol = 0
                }
                scrollOffset < historyMax -> {
                    scrollOffset = (scrollOffset + 1f).coerceAtMost(historyMax.toFloat())
                    indicatorCol = 0
                }
            }
        }
    }


    fun applyScrollDrag(deltaY: Float) {
        val historyMax = screenState.value.scrollback.size
        if (historyMax == 0) return
        val next = (scrollOffset + deltaY / lineHeightPx).coerceIn(0f, historyMax.toFloat())
        if (next == scrollOffset) return
        scrollOffset = next
        if (next > 0f) {
            isBrowsing = true
            indicatorRow = if (deltaY > 0) 0 else screenState.value.rows - 1
        } else {
            resetToLive()
        }
    }

    fun offNow(): Int = scrollOffset.roundToInt().coerceIn(0, screenState.value.scrollback.size)


    fun touchToCell(x: Float, y: Float): Pair<Int, Int> {
        val s = screenState.value
        val vpRow = (y / lineHeightPx).toInt().coerceIn(0, (s.rows - 1).coerceAtLeast(0))
        val col = (x / charWidthPx).toInt().coerceIn(0, (s.cols - 1).coerceAtLeast(0))
        val absRow = s.scrollback.size - offNow() + vpRow
        return absRow to col
    }

    fun copySelection() {
        val sel = selection ?: return
        val text = selectedText(screenState.value, sel)
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("terminal", text))
        Toast.makeText(context, if (text.isBlank()) "Nothing to copy" else "Copied", Toast.LENGTH_SHORT).show()
        selection = null
    }

    fun clipboardText(): String {
        val cb = context.getSystemService(ClipboardManager::class.java) ?: return ""
        val clip = cb.primaryClip ?: return ""
        if (clip.itemCount == 0) return ""
        return clip.getItemAt(0).coerceToText(context)?.toString() ?: ""
    }

    fun selectAll() {
        val s = screenState.value
        val total = s.scrollback.size + s.rows
        if (total == 0) return
        val lastRow = total - 1
        val lastCells = lineCellsAbs(s, lastRow)
        val lastCol = ((lastCells?.size ?: s.cols) - 1).coerceAtLeast(0)
        selection = TerminalSelection(0, 0, lastRow, lastCol)
        selAnchorPx = null
    }


    fun handleAt(x: Float, y: Float, sel: TerminalSelection): Int {
        val s = screenState.value
        val base = s.scrollback.size - offNow()
        val b = sel.ordered()
        val startVp = b.startRow - base
        val endVp = b.endRow - base
        val sx = b.startCol * charWidthPx
        val sy = (startVp + 1) * lineHeightPx
        val ex = (b.endCol + 1) * charWidthPx
        val ey = (endVp + 1) * lineHeightPx
        val dStart = hypot(x - sx, y - sy)
        val dEnd = hypot(x - ex, y - ey)
        return when {
            dStart <= handleTouchRadiusPx && dStart <= dEnd -> 1
            dEnd <= handleTouchRadiusPx -> 2
            else -> 0
        }
    }

    val onKeyRef = rememberUpdatedState(onKey)
    val onSpecialRef = rememberUpdatedState(onSpecialKey)
    val emitKey: (String) -> Unit = remember {
        { selection = null; resetToLive(); onKeyRef.value(it) }
    }
    val emitSpecial: (TerminalKey) -> Unit = remember {
        { selection = null; resetToLive(); onSpecialRef.value(it) }
    }

    TerminalVolumeScrollEffect(enabled = enableVolumeKeys, onVolumeKey = ::moveIndicator)

    var lastEmittedRows by remember { mutableIntStateOf(-1) }
    var lastEmittedCols by remember { mutableIntStateOf(-1) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(background),
    ) {
        val cols = (constraints.maxWidth / charWidthPx).toInt().coerceIn(1, 400)
        val rows = (constraints.maxHeight / lineHeightPx).toInt().coerceIn(1, 400)
        val viewportWidthPx = constraints.maxWidth.toFloat()
        val viewportHeightPx = constraints.maxHeight.toFloat()


        fun autoScrollForSelection(y: Float): Boolean {
            val edge = lineHeightPx * 1.2f
            return when {
                y < edge -> {
                    val before = scrollOffset
                    applyScrollDrag(lineHeightPx)
                    scrollOffset != before
                }
                y > viewportHeightPx - edge -> {
                    val before = scrollOffset
                    applyScrollDrag(-lineHeightPx)
                    scrollOffset != before
                }
                else -> false
            }
        }
        LaunchedEffect(rows, cols) {
            if (rows != lastEmittedRows || cols != lastEmittedCols) {
                lastEmittedRows = rows
                lastEmittedCols = cols
                onResize(rows, cols)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.fillMaxSize(),
            ) {
                val history = screen.scrollback
                val live = screen.lines
                val off = scrollOffset.roundToInt().coerceIn(0, history.size)
                val base = history.size - off
                val followingLive = off == 0
                val showLiveCursor = followingLive && !isBrowsing && selection == null && screen.cursorVisible
                val showBrowseCursor = selection == null && (isBrowsing || off > 0)
                val sel = selection
                val bounds = sel?.ordered()
                val selectionArgb = cursorColor.copy(alpha = 0.35f).toArgb()

                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    val baseline = -fm.ascent


                    if (bounds != null) {
                        paint.color = selectionArgb
                        paint.isUnderlineText = false
                        paint.isFakeBoldText = false
                        for (r in 0 until screen.rows) {
                            val absRow = base + r
                            if (absRow < bounds.startRow || absRow > bounds.endRow) continue
                            val from = if (absRow == bounds.startRow) bounds.startCol else 0
                            val to = if (absRow == bounds.endRow) bounds.endCol else screen.cols - 1
                            if (to < from) continue
                            val left = from * charWidthPx
                            val right = (to + 1) * charWidthPx
                            native.drawRect(left, r * lineHeightPx, right, (r + 1) * lineHeightPx, paint)
                        }
                    }

                    for (r in 0 until screen.rows) {
                        val srcIndex = base + r
                        if (srcIndex < 0) continue
                        val rowCells = if (srcIndex < history.size) {
                            history[srcIndex]
                        } else {
                            live.getOrNull(srcIndex - history.size)
                        } ?: continue
                        val liveRow = srcIndex - history.size

                        val cursorCol = when {
                            showLiveCursor && liveRow == screen.cursorRow -> screen.cursorCol
                            showBrowseCursor && r == indicatorRow -> indicatorCol
                            else -> -1
                        }

                        drawRow(
                            native = native,
                            paint = paint,
                            builder = glyphBuilder,
                            cells = rowCells,
                            top = r * lineHeightPx,
                            baseline = baseline,
                            charWidthPx = charWidthPx,
                            lineHeightPx = lineHeightPx,
                            foreground = foreground,
                            background = background,
                            cursorColor = cursorColor,
                            cursorCol = cursorCol,
                        )
                    }


                    if (bounds != null) {
                        paint.color = cursorColor.toArgb()
                        val startVp = bounds.startRow - base
                        val endVp = bounds.endRow - base
                        if (startVp in 0 until screen.rows) {
                            native.drawCircle(
                                bounds.startCol * charWidthPx,
                                (startVp + 1) * lineHeightPx,
                                handleRadiusPx,
                                paint,
                            )
                        }
                        if (endVp in 0 until screen.rows) {
                            native.drawCircle(
                                (bounds.endCol + 1) * charWidthPx,
                                (endVp + 1) * lineHeightPx,
                                handleRadiusPx,
                                paint,
                            )
                        }
                    }


                    if (maxOffset > 0) {
                        val trackW = 4f
                        val trackX = size.width - trackW - 2f
                        val fraction = 1f - (scrollOffset / maxOffset.coerceAtLeast(1))
                        val thumbH = (size.height * screen.rows / (maxOffset + screen.rows)).coerceAtLeast(16f)
                        val thumbTop = (size.height - thumbH) * fraction
                        paint.color = cursorColor.copy(alpha = 0.55f).toArgb()
                        native.drawRect(trackX, thumbTop, trackX + trackW, thumbTop + thumbH, paint)
                    }
                }
            }

            TerminalInputHost(
                onText = emitKey,
                onSpecialKey = emitSpecial,
                onVolumeKey = ::moveIndicator,
                requestKeyboardOnAttach = requestKeyboardOnAttach,
                onViewReady = { inputHost = it },
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {

                            suspend fun AwaitPointerEventScope.runSelectionDrag(
                                pointerId: PointerId,
                                anchorRow: Int,
                                anchorCol: Int,
                                start: Offset,
                            ) {
                                var lastPos = start
                                selAnchorPx = start
                                selecting = true
                                try {
                                    while (true) {
                                        val event = withTimeoutOrNull(AUTOSCROLL_INTERVAL_MS) { awaitPointerEvent() }
                                        if (event == null) {
                                            if (autoScrollForSelection(lastPos.y)) {
                                                val (r, c) = touchToCell(lastPos.x, lastPos.y)
                                                selection = TerminalSelection(anchorRow, anchorCol, r, c)
                                            }
                                            continue
                                        }
                                        val ch = event.changes.firstOrNull { it.id == pointerId } ?: break
                                        if (!ch.pressed) break
                                        ch.consume()
                                        lastPos = ch.position
                                        selAnchorPx = lastPos
                                        autoScrollForSelection(lastPos.y)
                                        val (r, c) = touchToCell(lastPos.x, lastPos.y)
                                        selection = TerminalSelection(anchorRow, anchorCol, r, c)
                                    }
                                } finally {
                                    selecting = false
                                }
                            }

                            while (true) {
                                val down = awaitFirstDown(requireUnconsumed = true)
                                val startPos = down.position
                                val activeSel = selection


                                val grabbed = if (activeSel != null) {
                                    handleAt(startPos.x, startPos.y, activeSel)
                                } else 0
                                if (grabbed != 0 && activeSel != null) {
                                    val b = activeSel.ordered()
                                    val anchorRow = if (grabbed == 1) b.endRow else b.startRow
                                    val anchorCol = if (grabbed == 1) b.endCol else b.startCol
                                    runSelectionDrag(down.id, anchorRow, anchorCol, startPos)
                                    continue
                                }


                                val decision = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    var d = "tap"
                                    while (true) {
                                        val e = awaitPointerEvent()
                                        val c = e.changes.firstOrNull { it.id == down.id }
                                        if (c == null) { d = "cancel"; break }
                                        if (!c.pressed) { d = "tap"; break }
                                        if ((c.position - startPos).getDistance() > viewConfiguration.touchSlop) {
                                            d = "drag"; break
                                        }
                                    }
                                    d
                                }

                                when (decision) {
                                    null -> {
                                        val (aRow, aCol) = touchToCell(startPos.x, startPos.y)
                                        val s = screenState.value
                                        val liveCursorAbs = s.scrollback.size + s.cursorRow
                                        val inTypingPlace = aRow == liveCursorAbs && aCol >= s.cursorCol
                                        if (inTypingPlace && clipboardText().isNotEmpty()) {

                                            selection = null
                                            pastePos = startPos
                                        } else {

                                            pastePos = null
                                            val cells = lineCellsAbs(s, aRow)
                                            val word = if (cells != null) wordRange(cells, aCol) else aCol..aCol
                                            selection = TerminalSelection(aRow, word.first, aRow, word.last)
                                            inputHost?.hideKeyboard()
                                            runSelectionDrag(down.id, aRow, word.first, startPos)
                                        }
                                    }
                                    "drag" -> {
                                        selection = null
                                        pastePos = null
                                        var lastY = startPos.y
                                        while (true) {
                                            val e = awaitPointerEvent()
                                            val c = e.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!c.pressed) break
                                            c.consume()
                                            val dy = c.position.y - lastY
                                            lastY = c.position.y
                                            applyScrollDrag(dy)
                                        }
                                    }
                                    "tap" -> {
                                        when {
                                            selection != null -> selection = null
                                            pastePos != null -> pastePos = null
                                            else -> inputHost?.showKeyboard()
                                        }
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    },
            )


            val sel = selection
            if (sel != null && !selecting) {
                val b = sel.ordered()
                val off = scrollOffset.roundToInt().coerceIn(0, screen.scrollback.size)
                val base = screen.scrollback.size - off
                val startVp = b.startRow - base
                var toolbarWidthPx by remember { mutableIntStateOf(0) }
                var toolbarHeightPx by remember { mutableIntStateOf(0) }
                val margin = lineHeightPx * 0.3f


                val anchor = selAnchorPx ?: Offset(b.startCol * charWidthPx, startVp * lineHeightPx)
                val aboveY = anchor.y - toolbarHeightPx - margin
                val belowY = anchor.y + lineHeightPx + margin
                val preferredY = if (aboveY >= 0f) aboveY else belowY
                val maxY = (viewportHeightPx - toolbarHeightPx - margin).coerceAtLeast(0f)
                val yPx = preferredY.coerceIn(0f, maxY)
                val xPx = anchor.x
                    .coerceIn(0f, (viewportWidthPx - toolbarWidthPx).coerceAtLeast(0f))

                Row(
                    modifier = Modifier
                        .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                        .background(Color(0xFF2B2D30), RoundedCornerShape(6.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .onGloballyPositioned {
                            toolbarWidthPx = it.size.width
                            toolbarHeightPx = it.size.height
                        },
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    ToolbarButton("Copy") { copySelection() }
                    ToolbarButton("Select all") { selectAll() }
                }
            }


            val pp = pastePos
            if (pp != null) {
                var pasteWidthPx by remember { mutableIntStateOf(0) }
                var pasteHeightPx by remember { mutableIntStateOf(0) }
                val aboveY = pp.y - pasteHeightPx - lineHeightPx * 0.5f
                val yPx = if (aboveY >= 0f) aboveY else pp.y + lineHeightPx * 0.5f
                val xPx = pp.x.coerceIn(0f, (viewportWidthPx - pasteWidthPx).coerceAtLeast(0f))

                Row(
                    modifier = Modifier
                        .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                        .background(Color(0xFF2B2D30), RoundedCornerShape(6.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .onGloballyPositioned {
                            pasteWidthPx = it.size.width
                            pasteHeightPx = it.size.height
                        },
                ) {
                    ToolbarButton("Paste") {
                        val text = clipboardText()
                        pastePos = null
                        if (text.isNotEmpty()) emitKey(text)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Color(0xFFE6E6E6),
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

private fun lineCellsAbs(screen: TerminalScreen, absRow: Int): List<TerminalCell>? =
    if (absRow < screen.scrollback.size) {
        screen.scrollback.getOrNull(absRow)
    } else {
        screen.lines.getOrNull(absRow - screen.scrollback.size)
    }

private fun isWordChar(ch: Char): Boolean =
    ch.isLetterOrDigit() || ch == '_' || ch == '-' || ch == '.' || ch == '/' || ch == '~' || ch == '+' || ch == '@'

private fun wordRange(cells: List<TerminalCell>, col: Int): IntRange {
    if (col !in cells.indices || !isWordChar(cells[col].char)) return col..col
    var start = col
    while (start > 0 && isWordChar(cells[start - 1].char)) start--
    var end = col
    while (end < cells.size - 1 && isWordChar(cells[end + 1].char)) end++
    return start..end
}

private fun selectedText(screen: TerminalScreen, sel: TerminalSelection): String {
    val b = sel.ordered()
    val sb = StringBuilder()
    for (absRow in b.startRow..b.endRow) {
        val cells = lineCellsAbs(screen, absRow)
        if (cells == null) {
            if (absRow != b.endRow) sb.append('\n')
            continue
        }
        val from = (if (absRow == b.startRow) b.startCol else 0).coerceIn(0, (cells.size - 1).coerceAtLeast(0))
        val to = (if (absRow == b.endRow) b.endCol else cells.size - 1).coerceIn(0, (cells.size - 1).coerceAtLeast(0))
        if (to >= from) {
            val part = (from..to).joinToString("") { cells[it].char.toString() }
            sb.append(part.trimEnd())
        }
        if (absRow != b.endRow) sb.append('\n')
    }
    return sb.toString()
}

@Composable
private fun TerminalInputHost(
    onText: (String) -> Unit,
    onSpecialKey: (TerminalKey) -> Unit,
    onVolumeKey: (volumeUp: Boolean) -> Unit,
    requestKeyboardOnAttach: Boolean,
    onViewReady: (TerminalInputEditText) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onTextRef = rememberUpdatedState(onText)
    val onSpecialRef = rememberUpdatedState(onSpecialKey)
    val onVolumeRef = rememberUpdatedState(onVolumeKey)
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TerminalInputEditText(ctx).apply {
                listener = object : TerminalInputListener {
                    override fun onText(text: String) = onTextRef.value(text)
                    override fun onSpecialKey(key: TerminalKey) = onSpecialRef.value(key)
                }
                volumeKeyHandler = { volumeUp ->
                    onVolumeRef.value(volumeUp)
                    true
                }
                if (requestKeyboardOnAttach) showKeyboard()
                onViewReady(this)
            }
        },
        update = { view ->
            view.listener = object : TerminalInputListener {
                override fun onText(text: String) = onTextRef.value(text)
                override fun onSpecialKey(key: TerminalKey) = onSpecialRef.value(key)
            }
            view.volumeKeyHandler = { volumeUp ->
                onVolumeRef.value(volumeUp)
                true
            }
            onViewReady(view)
        },

        onRelease = { view ->
            view.hideKeyboard()
            view.clearFocus()
        },
    )
}

private fun drawRow(
    native: android.graphics.Canvas,
    paint: Paint,
    builder: StringBuilder,
    cells: List<TerminalCell>,
    top: Float,
    baseline: Float,
    charWidthPx: Float,
    lineHeightPx: Float,
    foreground: Color,
    background: Color,
    cursorColor: Color,
    cursorCol: Int,
) {
    val n = cells.size
    var c = 0
    while (c < n) {
        val style = cells[c]
        var end = c + 1
        while (end < n && sameStyle(cells[end], style)) end++

        var fgArgb = ansiColor(style.fg, foreground).toArgb()
        var bgArgb = if (style.bg == DEFAULT_COLOR) null else ansiColor(style.bg, background).toArgb()
        if (style.inverse) {
            val tmp = fgArgb
            fgArgb = bgArgb ?: background.toArgb()
            bgArgb = tmp
        }

        val left = c * charWidthPx
        val right = end * charWidthPx
        if (bgArgb != null) {
            paint.color = bgArgb
            paint.isUnderlineText = false
            paint.isFakeBoldText = false
            native.drawRect(left, top, right, top + lineHeightPx, paint)
        }

        builder.setLength(0)
        var anyGlyph = false
        for (i in c until end) {
            val ch = cells[i].char
            builder.append(ch)
            if (ch != ' ') anyGlyph = true
        }
        if (anyGlyph) {
            paint.color = fgArgb
            paint.isFakeBoldText = style.bold
            paint.isUnderlineText = style.underline
            native.drawText(builder, 0, builder.length, left, top + baseline, paint)
        }
        c = end
    }

    if (cursorCol in 0 until n) {
        val left = cursorCol * charWidthPx
        paint.color = cursorColor.toArgb()
        paint.isUnderlineText = false
        paint.isFakeBoldText = false
        native.drawRect(left, top, left + charWidthPx, top + lineHeightPx, paint)
        val ch = cells[cursorCol].char
        if (ch != ' ') {
            paint.color = background.toArgb()
            native.drawText(ch.toString(), left, top + baseline, paint)
        }
    }
}

private fun sameStyle(a: TerminalCell, b: TerminalCell): Boolean =
    a.fg == b.fg && a.bg == b.bg && a.bold == b.bold && a.underline == b.underline && a.inverse == b.inverse

fun ansiColor(index: Int, default: Color): Color {
    if (index == DEFAULT_COLOR) return default
    if (index >= 256) {
        val rgb = index - 256
        return Color(0xFF000000.toInt() or (rgb and 0xFFFFFF))
    }
    if (index < 16) return ANSI_16[index]
    if (index < 232) {
        val n = index - 16
        val r = n / 36
        val g = (n % 36) / 6
        val b = n % 6
        fun step(v: Int) = if (v == 0) 0 else 55 + v * 40
        return Color(0xFF000000.toInt() or (step(r) shl 16) or (step(g) shl 8) or step(b))
    }
    val gray = 8 + (index - 232) * 10
    return Color(0xFF000000.toInt() or (gray shl 16) or (gray shl 8) or gray)
}

private val ANSI_16 = listOf(
    Color(0xFF000000), Color(0xFFCD3131), Color(0xFF0DBC79), Color(0xFFE5E510),
    Color(0xFF2472C8), Color(0xFFBC3FBC), Color(0xFF11A8CD), Color(0xFFE5E5E5),
    Color(0xFF666666), Color(0xFFF14C4C), Color(0xFF23D18B), Color(0xFFF5F543),
    Color(0xFF3B8EEA), Color(0xFFD670D6), Color(0xFF29B8DB), Color(0xFFFFFFFF),
)
