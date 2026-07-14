package com.ahmadkharfan.androidstudiolite.feature.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.ahmadkharfan.androidstudiolite.R
import com.ahmadkharfan.androidstudiolite.feature.terminal.emulator.DEFAULT_COLOR
import com.ahmadkharfan.androidstudiolite.feature.terminal.emulator.TerminalScreen
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import android.graphics.Paint
import android.graphics.Typeface

private const val FONT_SP = 13f
private const val LINE_HEIGHT_RATIO = 1.30f

/**
 * Renders an emulated [TerminalScreen] as a monospaced character grid and captures keyboard input,
 * forwarding printable text to [onKey] and non-printing keys to [onSpecialKey]. As the available space
 * changes it measures how many character cells fit and reports the new size through [onResize], which
 * drives the PTY window resize.
 *
 * Drawing goes straight to the native canvas (one run per row) for speed; the ANSI palette indices in
 * each cell are resolved to real colors via [ansiColor].
 */
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
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val typeface = remember { ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE }
    val focusRequester = remember { FocusRequester() }

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

    // A tiny transparent field owns the IME so the soft keyboard shows and text/keys reach us.
    var fieldValue by remember { mutableStateOf("") }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(background),
    ) {
        val cols = (constraints.maxWidth / charWidthPx).toInt().coerceIn(1, 400)
        val rows = (constraints.maxHeight / lineHeightPx).toInt().coerceIn(1, 400)
        LaunchedEffect(rows, cols) { onResize(rows, cols) }

        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val baseline = -fm.ascent
                for (r in screen.lines.indices) {
                    val row = screen.lines[r]
                    val top = r * lineHeightPx
                    for (c in row.indices) {
                        val cell = row[c]
                        val isCursor = screen.cursorVisible && r == screen.cursorRow && c == screen.cursorCol
                        var fgArgb = ansiColor(cell.fg, foreground).toArgb()
                        var bgArgb = if (cell.bg == DEFAULT_COLOR) null else ansiColor(cell.bg, background).toArgb()
                        if (cell.inverse) {
                            val tmp = fgArgb
                            fgArgb = bgArgb ?: background.toArgb()
                            bgArgb = tmp
                        }
                        val left = c * charWidthPx
                        if (isCursor) {
                            paint.color = cursorColor.toArgb()
                            native.drawRect(left, top, left + charWidthPx, top + lineHeightPx, paint)
                        } else if (bgArgb != null) {
                            paint.color = bgArgb
                            native.drawRect(left, top, left + charWidthPx, top + lineHeightPx, paint)
                        }
                        if (cell.char != ' ') {
                            paint.color = if (isCursor) background.toArgb() else fgArgb
                            paint.isFakeBoldText = cell.bold
                            paint.isUnderlineText = cell.underline
                            native.drawText(cell.char.toString(), left, top + baseline, paint)
                        }
                    }
                }
            }
        }

        BasicTextField(
            value = fieldValue,
            onValueChange = { new ->
                // Any text the IME commits is forwarded as keystrokes; keep the field empty.
                if (new.isNotEmpty()) {
                    onKey(new)
                    fieldValue = ""
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    handleHardwareKey(event.key, event.utf16CodePoint, onKey, onSpecialKey)
                },
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent, fontFamily = FontFamily(Font(R.font.jetbrains_mono))),
        )

        // Request focus on entry so the soft keyboard is available immediately.
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    }
}

/** Translate a hardware/IME key press to PTY bytes; returns true if consumed. */
private fun handleHardwareKey(
    key: Key,
    utf16CodePoint: Int,
    onKey: (String) -> Unit,
    onSpecialKey: (TerminalKey) -> Unit,
): Boolean {
    val special = when (key) {
        Key.Enter, Key.NumPadEnter -> TerminalKey.Enter
        Key.Backspace -> TerminalKey.Backspace
        Key.Tab -> TerminalKey.Tab
        Key.Escape -> TerminalKey.Escape
        Key.DirectionUp -> TerminalKey.ArrowUp
        Key.DirectionDown -> TerminalKey.ArrowDown
        Key.DirectionLeft -> TerminalKey.ArrowLeft
        Key.DirectionRight -> TerminalKey.ArrowRight
        else -> null
    }
    if (special != null) {
        onSpecialKey(special)
        return true
    }
    // A printable code point from a hardware keyboard (soft-keyboard text arrives via onValueChange).
    if (utf16CodePoint in 0x20..0x10FFFF) {
        onKey(String(Character.toChars(utf16CodePoint)))
        return true
    }
    return false
}

/**
 * Resolve an ANSI palette index to a color: 0–15 standard/bright, 16–255 the xterm 256-color set,
 * ≥256 packed 24-bit truecolor, [DEFAULT_COLOR] the caller's default.
 */
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
