package com.ahmadkharfan.androidstudiolite.feature.editor.view
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionItem
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionKind
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.KotlinSignatureHelpResolver
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.SignatureHelpResult
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.Diagnostic
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.DiagnosticSeverity
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorCompletionController
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorLanguage
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorSession
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.SmartEdit
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.bracketMatchAt
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.shiftDiagnostics
import kotlin.math.abs
import kotlin.math.max
data class CompletionOverlay(
    val items: List<CompletionItem>,
    val selectedIndex: Int,
    val anchorXpx: Float,
    val anchorYpx: Float,
)
data class SignatureHelpOverlay(
    val help: SignatureHelpResult,
    val anchorXpx: Float,
    val anchorYpx: Float,
)
data class DiagnosticMessageOverlay(
    val message: String,
    val severity: DiagnosticSeverity,
    val muted: Boolean,
    val anchorXpx: Float,
    val anchorYpx: Float,
)
class CodeEditorView(context: Context) : View(context) {
    init {
        clipToOutline = true
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
    }
    var session: EditorSession? = null
        private set
    private var onEdited: (() -> Unit)? = null
    private var onCaretMoved: ((line: Int, column: Int) -> Unit)? = null
    var onCompletionOverlay: ((CompletionOverlay?) -> Unit)? = null
    var onSignatureHelpOverlay: ((SignatureHelpOverlay?) -> Unit)? = null
    var onDiagnosticMessage: ((DiagnosticMessageOverlay?) -> Unit)? = null
    private val chipHitRects = ArrayList<Pair<android.graphics.RectF, Diagnostic>>()
    var analyzer: ((EditorSession) -> List<Diagnostic>)? = null
    var onDiagnostics: ((List<Diagnostic>) -> Unit)? = null
    private var palette: EditorPalette? = null
    private var textSizePx: Float = 0f
    private var tabSize: Int = 4
    private var densityScale: Float = 1f
    private var gitColorByLine: Map<Int, Int> = emptyMap()
    private var breakpointLines: Set<Int> = emptySet()
    private var lspKotlin = false
    private var lspJava = false
    private var lspXml = false
    private var bottomScrollPaddingPx: Float = 0f
    private var lineHeightPx: Float = 0f
    private var charWidthPx: Float = 0f
    private var baselineOffsetPx: Float = 0f
    private var glyphTopOffsetPx: Float = 0f
    private var glyphHeightPx: Float = 0f
    private var gutterWidthPx: Float = 0f
    private var maxLineChars: Int = 0
    private var scrollXpx: Float = 0f
    private var scrollYpx: Float = 0f
    private var renderedActiveLineTop: Float = 0f
    private var findQuery: String = ""
    private var findCurrentIndex: Int = 0
    private var findMatches: List<Int> = emptyList()
    private val completionController = EditorCompletionController(
        lspEnabled = { language ->
            when (language) {
                EditorLanguage.Kotlin -> lspKotlin
                EditorLanguage.Java -> lspJava
                EditorLanguage.Xml -> lspXml
                EditorLanguage.Plain -> false
            }
        },
    )
    /** Feed the latest synced project/dependency symbols into completion (diagnostics read them via the analyzer). */
    fun setProjectIndex(index: com.ahmadkharfan.androidstudiolite.feature.editor.engine.project.ProjectSymbolIndex) {
        if (completionController.projectIndex !== index) {
            completionController.projectIndex = index
            if (session != null) scheduleAnalysis()
        }
    }
    private var completionActive = false
    private var completionItems: List<CompletionItem> = emptyList()
    private var completionSelected = 0
    private var completionSuppressNext = false
    private var signatureHelpDismissed = false
    private var signatureHelpAutoHidden = false
    private var signatureHelpCallStart = -1
    private val handler = Handler(Looper.getMainLooper())
    private val completionRunnable = Runnable { runCompletion() }
    private val signatureHelpRunnable = Runnable { runSignatureHelp() }
    private val signatureHelpHideRunnable = Runnable { autoHideSignatureHelp() }
    private val analysisRunnable = Runnable { runAnalysis() }
    private var diagLineSeverity: Map<Int, DiagnosticSeverity> = emptyMap()
    private var caretAlpha: Float = 1f
    private val caretAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
        duration = BLINK_MS
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            caretAlpha = it.animatedValue as Float
            invalidateCaret()
        }
    }
    private var animStartX = 0f
    private var animStartY = 0f
    private var animStartActive = 0f
    private var animTargetX = 0f
    private var animTargetY = 0f
    private var animTargetActive = 0f
    private val visualAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = SCROLL_ANIM_MS
        interpolator = DecelerateInterpolator()
        addUpdateListener {
            val f = it.animatedValue as Float
            scrollXpx = lerp(animStartX, animTargetX, f)
            scrollYpx = lerp(animStartY, animTargetY, f)
            renderedActiveLineTop = lerp(animStartActive, animTargetActive, f)
            invalidate()
        }
    }
    private val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isSubpixelText = true }
    private val gutterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val squigglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isSubpixelText = true }
    private val squigglePath = Path()
    private var diagChipByLine: Map<Int, Diagnostic> = emptyMap()
    private val gestureDetector = GestureDetector(context, GestureListener())
    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
    }
    fun bind(
        session: EditorSession,
        onEdited: () -> Unit,
        onCaretMoved: (line: Int, column: Int) -> Unit,
    ) {
        this.onEdited = onEdited
        this.onCaretMoved = onCaretMoved
        if (this.session !== session) {
            this.session = session
            handlesVisible = false
            dragMode = DragMode.NONE
            scrollXpx = 0f
            scrollYpx = 0f
            renderedActiveLineTop = 0f
            dismissCompletion()
            recomputeMaxLineChars()
            runAnalysis()
            resetBlink()
            val caret = session.caretPosition
            onCaretMoved(caret.line, caret.column)
            invalidate()
        }
    }
    fun updateOptions(
        textSizePx: Float,
        tabSize: Int,
        palette: EditorPalette,
        typeface: Typeface?,
        densityScale: Float,
        gitColorByLine: Map<Int, Int>,
        breakpointLines: Set<Int>,
        lspKotlin: Boolean,
        lspJava: Boolean,
        lspXml: Boolean,
    ) {
        val metricsChanged = this.textSizePx != textSizePx
        this.textSizePx = textSizePx
        this.tabSize = tabSize
        this.palette = palette
        this.densityScale = densityScale
        this.gitColorByLine = gitColorByLine
        this.breakpointLines = breakpointLines
        val kotlinLspChanged = lspKotlin != this.lspKotlin
        val lspChanged = kotlinLspChanged || lspJava != this.lspJava || lspXml != this.lspXml
        this.lspKotlin = lspKotlin
        this.lspJava = lspJava
        this.lspXml = lspXml
        if (kotlinLspChanged) {
            completionController.invalidateProviders()
        }
        if (lspChanged && session != null) scheduleAnalysis()
        val face = typeface ?: Typeface.MONOSPACE
        codePaint.typeface = face
        codePaint.textSize = textSizePx
        gutterPaint.typeface = face
        gutterPaint.textSize = textSizePx * GUTTER_TEXT_RATIO
        chipPaint.typeface = face
        chipPaint.textSize = textSizePx * CHIP_TEXT_RATIO
        if (metricsChanged || lineHeightPx == 0f) {
            lineHeightPx = textSizePx * LINE_HEIGHT_RATIO
            charWidthPx = codePaint.measureText("m")
            val fm = codePaint.fontMetrics
            glyphHeightPx = fm.descent - fm.ascent
            glyphTopOffsetPx = (lineHeightPx - glyphHeightPx) / 2f
            baselineOffsetPx = glyphTopOffsetPx - fm.ascent
            renderedActiveLineTop = (session?.caretPosition?.line ?: 0) * lineHeightPx
            bottomScrollPaddingPx = lineHeightPx * 4f
        }
        updateGutterWidth()
        clampScroll()
        invalidate()
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h != oldh && h > 0) {
            clampScroll()
            ensureCaretVisibleAnimated()
        }
    }
    fun setFind(query: String, currentIndex: Int) {
        val changedQuery = query != findQuery
        findQuery = query
        findCurrentIndex = currentIndex
        if (changedQuery) recomputeFindMatches()
        scrollCurrentFindMatchIntoView()
        invalidate()
    }
    private fun updateGutterWidth() {
        val digits = max(2, session?.lineCount?.toString()?.length ?: 2)
        val numberWidth = max(dp(30f), digits * gutterPaint.measureText("0") + dp(8f))
        gutterWidthPx = dp(GUTTER_START_DP) + dp(DOT_SIZE_DP) + dp(4f) + numberWidth + dp(6f) + dp(GIT_BAR_W_DP) + dp(6f)
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        resetBlink()
    }
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        caretAnimator.cancel()
        visualAnimator.cancel()
        handler.removeCallbacks(completionRunnable)
        handler.removeCallbacks(signatureHelpRunnable)
        handler.removeCallbacks(signatureHelpHideRunnable)
        handler.removeCallbacks(analysisRunnable)
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) resetBlink() else caretAnimator.cancel()
    }
    private fun resetBlink() {
        caretAlpha = 1f
        caretAnimator.cancel()
        caretAnimator.start()
        invalidateCaret()
    }
    override fun onDraw(canvas: Canvas) {
        val session = session ?: return
        val palette = palette ?: return
        if (lineHeightPx <= 0f) return
        canvas.drawColor(palette.canvas)
        val lineCount = session.lineCount
        val caret = session.caretPosition
        val sel = session.selection
        val firstLine = (scrollYpx / lineHeightPx).toInt().coerceIn(0, lineCount - 1)
        val lastLine = ((scrollYpx + viewportHeightPx()) / lineHeightPx).toInt().coerceIn(0, lineCount - 1)
        val codeLeft = gutterWidthPx + dp(CODE_PADDING_DP)
        val activeTop = renderedActiveLineTop - scrollYpx
        fillPaint.color = palette.lineHighlight
        canvas.drawRect(gutterWidthPx, activeTop, width.toFloat(), activeTop + lineHeightPx, fillPaint)
        canvas.save()
        canvas.clipRect(gutterWidthPx, 0f, width.toFloat(), height.toFloat())
        if (findQuery.isNotEmpty() && findMatches.isNotEmpty()) {
            drawFindMatches(canvas, session, palette, codeLeft)
        }
        if (!sel.isCollapsed) {
            drawSelection(canvas, session, palette, firstLine, lastLine, codeLeft)
        }
        bracketMatchAt(session.document, session.selection.caret)?.let { (a, b) ->
            drawBracketBox(canvas, session, palette, a, codeLeft)
            drawBracketBox(canvas, session, palette, b, codeLeft)
        }
        for (line in firstLine..lastLine) {
            drawCodeLine(canvas, session, palette, line, codeLeft)
        }
        if (session.diagnostics.isNotEmpty()) {
            drawDiagnostics(canvas, session, palette, firstLine, lastLine, codeLeft)
        }
        if (diagChipByLine.isNotEmpty()) {
            drawDiagnosticChips(canvas, session, palette, firstLine, lastLine, codeLeft)
        }
        if (sel.isCollapsed && isFocused) {
            val x = codeLeft + caret.column * charWidthPx - scrollXpx
            val top = caret.line * lineHeightPx - scrollYpx + glyphTopOffsetPx
            fillPaint.color = withAlpha(palette.cursor, caretAlpha)
            canvas.drawRect(x, top, x + dp(CARET_W_DP), top + glyphHeightPx, fillPaint)
            fillPaint.alpha = 255
        }
        canvas.restore()
        drawGutter(canvas, session, palette, firstLine, lastLine, caret.line)
        if (handlesVisible && !sel.isCollapsed) {
            drawHandles(canvas, session, palette, codeLeft)
        }
    }
    private fun drawCodeLine(canvas: Canvas, session: EditorSession, palette: EditorPalette, line: Int, codeLeft: Float) {
        val text = session.document.lineText(line).replace('\t', ' ')
        if (text.isEmpty()) return
        val baseX = codeLeft - scrollXpx
        val baseY = line * lineHeightPx - scrollYpx + baselineOffsetPx
        val tokens = session.tokensForLine(line)
        var cursor = 0
        for (token in tokens) {
            val start = token.start.coerceIn(0, text.length)
            val end = token.end.coerceIn(start, text.length)
            if (start > cursor) {
                codePaint.color = palette.defaultText
                canvas.drawText(text, cursor, start, baseX + cursor * charWidthPx, baseY, codePaint)
            }
            codePaint.color = palette.colorFor(token.type)
            canvas.drawText(text, start, end, baseX + start * charWidthPx, baseY, codePaint)
            cursor = end
        }
        if (cursor < text.length) {
            codePaint.color = palette.defaultText
            canvas.drawText(text, cursor, text.length, baseX + cursor * charWidthPx, baseY, codePaint)
        }
    }
    private fun drawSelection(canvas: Canvas, session: EditorSession, palette: EditorPalette, firstLine: Int, lastLine: Int, codeLeft: Float) {
        val doc = session.document
        val sel = session.selection
        val startPos = doc.offsetToPosition(sel.start)
        val endPos = doc.offsetToPosition(sel.end)
        fillPaint.color = palette.selection
        for (line in max(firstLine, startPos.line)..minOf(lastLine, endPos.line)) {
            val lineLen = doc.lineText(line).length
            val fromCol = if (line == startPos.line) startPos.column else 0
            val toCol = if (line == endPos.line) endPos.column else lineLen + 1
            val left = codeLeft + fromCol * charWidthPx - scrollXpx
            val right = codeLeft + toCol * charWidthPx - scrollXpx
            val top = line * lineHeightPx - scrollYpx + glyphTopOffsetPx
            canvas.drawRect(left, top, right, top + glyphHeightPx, fillPaint)
        }
    }
    private fun drawFindMatches(canvas: Canvas, session: EditorSession, palette: EditorPalette, codeLeft: Float) {
        val doc = session.document
        val len = findQuery.length
        for ((index, start) in findMatches.withIndex()) {
            val pos = doc.offsetToPosition(start)
            val top = pos.line * lineHeightPx - scrollYpx + glyphTopOffsetPx
            if (top + glyphHeightPx < 0 || top > height) continue
            val left = codeLeft + pos.column * charWidthPx - scrollXpx
            val right = left + len * charWidthPx
            fillPaint.color = if (index + 1 == findCurrentIndex) palette.findCurrent else palette.findMatch
            canvas.drawRect(left, top, right, top + glyphHeightPx, fillPaint)
        }
    }
    private fun drawBracketBox(canvas: Canvas, session: EditorSession, palette: EditorPalette, offset: Int, codeLeft: Float) {
        val pos = session.document.offsetToPosition(offset)
        val left = codeLeft + pos.column * charWidthPx - scrollXpx
        val top = pos.line * lineHeightPx - scrollYpx + glyphTopOffsetPx
        strokePaint.color = palette.bracketMatch
        strokePaint.strokeWidth = dp(1f)
        canvas.drawRect(left, top, left + charWidthPx, top + glyphHeightPx, strokePaint)
    }
    private fun drawDiagnosticChips(canvas: Canvas, session: EditorSession, palette: EditorPalette, firstLine: Int, lastLine: Int, codeLeft: Float) {
        val doc = session.document
        val fm = chipPaint.fontMetrics
        val chipGlyphH = fm.descent - fm.ascent
        val padH = dp(8f)
        val gap = dp(24f)
        chipHitRects.clear()
        for (line in firstLine..lastLine) {
            val d = diagChipByLine[line] ?: continue
            val color = when (d.severity) {
                DiagnosticSeverity.Error -> palette.diagnosticError
                DiagnosticSeverity.Warning -> if (d.muted) palette.gutterTextInactive else palette.diagnosticWarning
                else -> continue
            }
            val lineLen = doc.lineText(line).length
            val startX = codeLeft + lineLen * charWidthPx - scrollXpx + gap
            val avail = width - startX - dp(12f)
            if (avail < dp(40f)) continue
            val msg = ellipsize(d.message, avail - padH * 2, chipPaint)
            if (msg.isEmpty()) continue
            val textW = chipPaint.measureText(msg)
            val chipW = textW + padH * 2
            val chipTop = line * lineHeightPx - scrollYpx + (lineHeightPx - chipGlyphH) / 2f - dp(1f)
            val chipH = chipGlyphH + dp(2f)
            val r = dp(4f)
            fillPaint.color = withAlpha(color, 0.16f)
            canvas.drawRoundRect(startX, chipTop, startX + chipW, chipTop + chipH, r, r, fillPaint)
            fillPaint.alpha = 255
            chipPaint.color = color
            canvas.drawText(msg, startX + padH, chipTop + dp(1f) - fm.ascent, chipPaint)
            chipHitRects.add(android.graphics.RectF(startX, chipTop, startX + chipW, chipTop + chipH) to d)
        }
    }
    private fun diagnosticAt(x: Float, y: Float): Diagnostic? {
        for ((rect, d) in chipHitRects) if (rect.contains(x, y)) return d
        val session = session ?: return null
        val off = offsetAt(x, y)
        return session.diagnostics.firstOrNull { off >= it.start && off < it.end && it.end > it.start }
    }
    private fun ellipsize(text: String, maxWidth: Float, paint: Paint): String {
        if (maxWidth <= 0f) return ""
        if (paint.measureText(text) <= maxWidth) return text
        val ell = "…"
        val ellW = paint.measureText(ell)
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + ellW > maxWidth) end--
        return if (end <= 0) "" else text.substring(0, end) + ell
    }
    private fun drawDiagnostics(canvas: Canvas, session: EditorSession, palette: EditorPalette, firstLine: Int, lastLine: Int, codeLeft: Float) {
        val doc = session.document
        val amplitude = dp(1.6f)
        val waveLen = dp(4f)
        for (diagnostic in session.diagnostics) {
            val startPos = doc.offsetToPosition(diagnostic.start.coerceIn(0, doc.length))
            if (startPos.line < firstLine || startPos.line > lastLine) continue
            val endPos = doc.offsetToPosition(diagnostic.end.coerceIn(0, doc.length))
            val endCol = if (endPos.line == startPos.line) endPos.column else doc.lineText(startPos.line).length
            val left = codeLeft + startPos.column * charWidthPx - scrollXpx
            val right = codeLeft + endCol * charWidthPx - scrollXpx
            val y = startPos.line * lineHeightPx - scrollYpx + glyphTopOffsetPx + glyphHeightPx
            squigglePaint.color = when (diagnostic.severity) {
                DiagnosticSeverity.Error -> palette.diagnosticError
                DiagnosticSeverity.Warning -> palette.diagnosticWarning
                DiagnosticSeverity.Info, DiagnosticSeverity.Hint -> palette.diagnosticHint
            }
            if (diagnostic.muted) {
                squigglePaint.strokeWidth = dp(0.9f)
                canvas.drawLine(left, y, right, y, squigglePaint)
                continue
            }
            squigglePaint.strokeWidth = dp(1.2f)
            squigglePath.reset()
            squigglePath.moveTo(left, y)
            var x = left
            var up = true
            while (x < right) {
                val nextX = (x + waveLen).coerceAtMost(right)
                squigglePath.lineTo(nextX, if (up) y - amplitude else y + amplitude)
                x = nextX
                up = !up
            }
            canvas.drawPath(squigglePath, squigglePaint)
        }
    }
    private fun drawGutter(canvas: Canvas, session: EditorSession, palette: EditorPalette, firstLine: Int, lastLine: Int, activeLine: Int) {
        gutterPaint.color = palette.gutter
        canvas.drawRect(0f, 0f, gutterWidthPx, height.toFloat(), gutterPaint)
        fillPaint.color = palette.divider
        canvas.drawRect(gutterWidthPx - dp(1f), 0f, gutterWidthPx, height.toFloat(), fillPaint)
        val numberLeft = dp(GUTTER_START_DP) + dp(DOT_SIZE_DP) + dp(4f)
        for (line in firstLine..lastLine) {
            val top = line * lineHeightPx - scrollYpx
            val r = dp(DOT_SIZE_DP) / 2f
            if (line in breakpointLines) {
                fillPaint.color = palette.breakpoint
                canvas.drawCircle(dp(GUTTER_START_DP) + r, top + lineHeightPx / 2f, r, fillPaint)
            } else {
                diagLineSeverity[line]?.let { severity ->
                    fillPaint.color = when (severity) {
                        DiagnosticSeverity.Error -> palette.diagnosticError
                        DiagnosticSeverity.Warning -> palette.diagnosticWarning
                        DiagnosticSeverity.Info, DiagnosticSeverity.Hint -> palette.diagnosticHint
                    }
                    canvas.drawCircle(dp(GUTTER_START_DP) + r, top + lineHeightPx / 2f, r * 0.7f, fillPaint)
                }
            }
            gutterPaint.color = if (line == activeLine) palette.gutterTextActive else palette.gutterTextInactive
            canvas.drawText((line + 1).toString(), numberLeft, top + baselineOffsetPx, gutterPaint)
            gitColorByLine[line]?.let { color ->
                fillPaint.color = color
                val barTop = top + (lineHeightPx - dp(GIT_BAR_H_DP)) / 2f
                val barLeft = gutterWidthPx - dp(6f) - dp(GIT_BAR_W_DP)
                canvas.drawRect(barLeft, barTop, barLeft + dp(GIT_BAR_W_DP), barTop + dp(GIT_BAR_H_DP), fillPaint)
            }
        }
    }
    private fun drawHandles(canvas: Canvas, session: EditorSession, palette: EditorPalette, codeLeft: Float) {
        val doc = session.document
        val sel = session.selection
        val r = dp(HANDLE_RADIUS_DP)
        fillPaint.color = palette.cursor
        for (offset in intArrayOf(sel.start, sel.end)) {
            val pos = doc.offsetToPosition(offset)
            val x = codeLeft + pos.column * charWidthPx - scrollXpx
            val y = pos.line * lineHeightPx - scrollYpx + glyphTopOffsetPx + glyphHeightPx + r
            canvas.drawCircle(x, y, r, fillPaint)
        }
    }
    private enum class DragMode { NONE, SCROLL, SELECT }
    private var dragMode = DragMode.NONE
    private var handlesVisible = false
    private var draggingStartHandle = false
    private var draggingEndHandle = false
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            dragMode = DragMode.NONE
            draggingStartHandle = false
            draggingEndHandle = false
        }
        return handled || super.onTouchEvent(event)
    }
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            val session = session
            if (handlesVisible && session != null && !session.selection.isCollapsed) {
                val codeLeft = gutterWidthPx + dp(CODE_PADDING_DP)
                val (startX, startY) = handleCenter(session, session.selection.start, codeLeft)
                val (endX, endY) = handleCenter(session, session.selection.end, codeLeft)
                val touchR = dp(HANDLE_RADIUS_DP) * 2.4f
                when {
                    dist(e.x, e.y, startX, startY) <= touchR -> { draggingStartHandle = true; dragMode = DragMode.SELECT }
                    dist(e.x, e.y, endX, endY) <= touchR -> { draggingEndHandle = true; dragMode = DragMode.SELECT }
                }
            }
            return true
        }
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val session = session ?: return false
            val diag = diagnosticAt(e.x, e.y)
            if (diag != null) {
                onDiagnosticMessage?.invoke(DiagnosticMessageOverlay(diag.message, diag.severity, diag.muted, e.x, e.y))
                focusAndShowKeyboard()
                return true
            }
            onDiagnosticMessage?.invoke(null)
            handlesVisible = false
            dismissCompletion()
            session.setCaret(offsetAt(e.x, e.y))
            afterCaretChange()
            focusAndShowKeyboard()
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            selectWordAt(e.x, e.y)
            focusAndShowKeyboard()
            return true
        }
        override fun onLongPress(e: MotionEvent) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            selectWordAt(e.x, e.y)
            dragMode = DragMode.SELECT
            draggingEndHandle = true
            focusAndShowKeyboard()
        }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            val session = session ?: return false
            if (dragMode == DragMode.SELECT) {
                val offset = offsetAt(e2.x, e2.y)
                val sel = session.selection
                if (draggingStartHandle) session.setSelection(anchor = sel.end, caret = offset)
                else session.setSelection(anchor = sel.start, caret = offset)
                afterCaretChange()
                return true
            }
            dragMode = DragMode.SCROLL
            visualAnimator.cancel()
            scrollXpx += distanceX
            scrollYpx += distanceY
            clampScroll()
            invalidate()
            return true
        }
    }
    private fun handleCenter(session: EditorSession, offset: Int, codeLeft: Float): Pair<Float, Float> {
        val pos = session.document.offsetToPosition(offset)
        val x = codeLeft + pos.column * charWidthPx - scrollXpx
        val y = pos.line * lineHeightPx - scrollYpx + glyphTopOffsetPx + glyphHeightPx + dp(HANDLE_RADIUS_DP)
        return x to y
    }
    private fun offsetAt(x: Float, y: Float): Int {
        val session = session ?: return 0
        val doc = session.document
        val codeLeft = gutterWidthPx + dp(CODE_PADDING_DP)
        val line = ((y + scrollYpx) / lineHeightPx).toInt().coerceIn(0, doc.lineCount - 1)
        val col = ((x - codeLeft + scrollXpx) / charWidthPx).let { Math.round(it) }.coerceAtLeast(0)
        val lineStart = doc.lineStartOffset(line)
        val lineLen = doc.lineText(line).length
        return lineStart + col.coerceAtMost(lineLen)
    }
    private fun selectWordAt(x: Float, y: Float) {
        val session = session ?: return
        val doc = session.document
        val offset = offsetAt(x, y)
        val text = doc.text
        var start = offset
        var end = offset
        while (start > 0 && isWordChar(text[start - 1])) start--
        while (end < text.length && isWordChar(text[end])) end++
        if (start == end) {
            session.setCaret(offset)
            handlesVisible = false
        } else {
            session.setSelection(start, end)
            handlesVisible = true
        }
        dismissCompletion()
        afterCaretChange()
    }
    override fun onCheckIsTextEditor(): Boolean = true
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_ACTION_NONE
        outAttrs.initialSelStart = session?.selection?.start ?: 0
        outAttrs.initialSelEnd = session?.selection?.end ?: 0
        val connection = CodeInputConnection()
        activeInputConnection = connection
        return connection
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val session = session ?: return super.onKeyDown(keyCode, event)
        if (completionActive) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> { moveCompletionSelection(-1); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { moveCompletionSelection(1); return true }
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_TAB -> {
                    if (shouldAcceptCompletionOnEnter()) {
                        acceptSelectedCompletion()
                    } else {
                        dismissCompletion()
                        edit { SmartEdit.typeChar(session, '\n', tabSize) }
                        afterTextEditTriggers(session, typedChar = '\n')
                    }
                    return true
                }
                KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> {
                    if (completionActive) dismissCompletion() else dismissSignatureHelp()
                    return true
                }
            }
        }
        if (event.isCtrlPressed) {
            when (keyCode) {
                KeyEvent.KEYCODE_Z -> { edit { if (event.isShiftPressed) session.redo() else session.undo() }; return true }
                KeyEvent.KEYCODE_Y -> { edit { session.redo() }; return true }
                KeyEvent.KEYCODE_C -> { copySelection(); return true }
                KeyEvent.KEYCODE_X -> { cutSelection(); return true }
                KeyEvent.KEYCODE_V -> { pasteClipboard(); return true }
                KeyEvent.KEYCODE_A -> { session.selectAll(); handlesVisible = true; dismissCompletion(); afterCaretChange(); return true }
                KeyEvent.KEYCODE_SPACE -> { triggerCompletion(); return true }
            }
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> { edit { SmartEdit.backspace(session, tabSize) }; afterTextEditTriggers(session, isBackspace = true); return true }
            KeyEvent.KEYCODE_ENTER -> {
                if (completionActive && shouldAcceptCompletionOnEnter()) {
                    acceptSelectedCompletion()
                } else {
                    dismissCompletion()
                    edit { SmartEdit.typeChar(session, '\n', tabSize) }
                    afterTextEditTriggers(session, typedChar = '\n')
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> { moveCaret(-1, event.isShiftPressed); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { moveCaret(1, event.isShiftPressed); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { moveCaretVertical(-1, event.isShiftPressed); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { moveCaretVertical(1, event.isShiftPressed); return true }
        }
        val ch = event.unicodeChar
        if (ch != 0 && !event.isCtrlPressed) {
            val c = ch.toChar()
            edit { SmartEdit.typeChar(session, c, tabSize) }
            afterTextEditTriggers(session, typedChar = c)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
    private fun moveCaret(delta: Int, extend: Boolean) {
        val session = session ?: return
        val sel = session.selection
        val target = (sel.caret + delta).coerceIn(0, session.document.length)
        session.setSelection(anchor = if (extend) sel.anchor else target, caret = target)
        handlesVisible = false
        dismissCompletion()
        afterCaretChange()
    }
    private fun moveCaretVertical(deltaLines: Int, extend: Boolean) {
        val session = session ?: return
        val doc = session.document
        val sel = session.selection
        val pos = doc.offsetToPosition(sel.caret)
        val targetLine = (pos.line + deltaLines).coerceIn(0, doc.lineCount - 1)
        val target = doc.positionToOffset(targetLine, pos.column)
        session.setSelection(anchor = if (extend) sel.anchor else target, caret = target)
        handlesVisible = false
        dismissCompletion()
        afterCaretChange()
    }
    fun revealOffset(offset: Int) {
        val session = session ?: return
        session.setCaret(offset.coerceIn(0, session.document.length))
        dismissCompletion()
        handlesVisible = false
        if (!isFocused) requestFocus()
        afterCaretChange()
    }
    fun triggerCompletion() {
        handler.removeCallbacks(completionRunnable)
        runCompletion()
    }
    private fun scheduleCompletion() {
        handler.removeCallbacks(completionRunnable)
        handler.postDelayed(completionRunnable, COMPLETION_DEBOUNCE_MS)
    }
    private fun runCompletion() {
        val session = session ?: return
        val items = completionController.query(session)
        if (items.isEmpty()) {
            dismissCompletion()
            refreshSignatureHelp(immediate = true)
            return
        }
        completionItems = items
        completionSelected = completionSelected.coerceIn(0, items.size - 1)
        completionActive = true
        clearSignatureHelpOverlay()
        reportCompletionOverlay()
    }
    private fun moveCompletionSelection(delta: Int) {
        if (!completionActive || completionItems.isEmpty()) return
        val n = completionItems.size
        completionSelected = ((completionSelected + delta) % n + n) % n
        reportCompletionOverlay()
    }
    fun acceptCompletionAt(index: Int) {
        val session = session ?: return
        val item = completionItems.getOrNull(index) ?: return
        dismissCompletion()
        activeInputConnection?.clearComposing()
        edit { completionController.accept(session, item) }
        activeInputConnection?.finishComposingText()
        syncImeSelection()
        completionSuppressNext = true
    }
    private fun acceptSelectedCompletion() = acceptCompletionAt(completionSelected)
    private fun shouldAcceptCompletionOnEnter(): Boolean {
        if (!completionActive || completionItems.isEmpty()) return false
        val session = session ?: return false
        val ctx = completionController.buildContext(session)
        if (ctx.prefix.isEmpty()) {
            val onlyNoise = completionItems.all {
                it.kind == CompletionKind.Keyword || it.kind == CompletionKind.Snippet
            }
            if (onlyNoise) return false
        }
        return true
    }
    fun dismissCompletion() {
        handler.removeCallbacks(completionRunnable)
        val wasActive = completionActive || completionItems.isNotEmpty()
        completionActive = false
        completionItems = emptyList()
        completionSelected = 0
        onCompletionOverlay?.invoke(null)
        if (wasActive) {
            refreshSignatureHelp(immediate = true)
        }
    }
    private fun reportCompletionOverlay() {
        val session = session ?: return
        val caret = session.caretPosition
        val codeLeft = gutterWidthPx + dp(CODE_PADDING_DP)
        val anchorX = codeLeft + caret.column * charWidthPx - scrollXpx
        val anchorY = caret.line * lineHeightPx - scrollYpx + lineHeightPx
        onCompletionOverlay?.invoke(CompletionOverlay(completionItems, completionSelected, anchorX, anchorY))
    }
    private fun clearSignatureHelpOverlay() {
        handler.removeCallbacks(signatureHelpRunnable)
        handler.removeCallbacks(signatureHelpHideRunnable)
        onSignatureHelpOverlay?.invoke(null)
    }
    private fun scheduleSignatureHelp() {
        handler.removeCallbacks(signatureHelpRunnable)
        handler.postDelayed(signatureHelpRunnable, SIGNATURE_HELP_DEBOUNCE_MS)
    }
    private fun syncSignatureHelpNow() {
        val session = session ?: run {
            clearSignatureHelpOverlay()
            return
        }
        if (session.language != EditorLanguage.Kotlin) {
            resetSignatureHelpState()
            clearSignatureHelpOverlay()
            return
        }
        val callStart = KotlinSignatureHelpResolver.enclosingCallParen(session.text, session.selection.caret)
        if (callStart < 0) {
            resetSignatureHelpState()
            clearSignatureHelpOverlay()
            return
        }
        if (callStart != signatureHelpCallStart) {
            signatureHelpCallStart = callStart
            signatureHelpDismissed = false
            signatureHelpAutoHidden = false
        }
        if (!shouldOfferSignatureHelp(session)) {
            clearSignatureHelpOverlay()
            return
        }
        scheduleSignatureHelp()
    }
    private fun armSignatureHelp() {
        val session = session ?: return
        signatureHelpCallStart = KotlinSignatureHelpResolver.enclosingCallParen(session.text, session.selection.caret)
        signatureHelpDismissed = false
        signatureHelpAutoHidden = false
        syncSignatureHelpNow()
    }
    private fun autoHideSignatureHelp() {
        signatureHelpAutoHidden = true
        onSignatureHelpOverlay?.invoke(null)
    }
    private fun resetSignatureHelpState() {
        signatureHelpCallStart = -1
        signatureHelpDismissed = false
        signatureHelpAutoHidden = false
    }
    private fun refreshSignatureHelp(immediate: Boolean) {
        if (immediate) {
            handler.removeCallbacks(signatureHelpRunnable)
            runSignatureHelp()
        } else {
            syncSignatureHelpNow()
        }
    }
    private fun shouldOfferSignatureHelp(session: EditorSession): Boolean {
        if (session.language != EditorLanguage.Kotlin) return false
        if (signatureHelpDismissed) return false
        if (signatureHelpAutoHidden) return false
        if (completionActive && completionItems.isNotEmpty()) return false
        val caret = session.selection.caret
        return KotlinSignatureHelpResolver.caretInsideCall(session.text, caret)
    }
    private fun runSignatureHelp() {
        val session = session ?: run {
            clearSignatureHelpOverlay()
            return
        }
        if (!shouldOfferSignatureHelp(session)) {
            if (!KotlinSignatureHelpResolver.caretInsideCall(session.text, session.selection.caret)) {
                signatureHelpDismissed = false
            }
            clearSignatureHelpOverlay()
            return
        }
        val help = KotlinSignatureHelpResolver.fromCallSite(session.text, session.selection.caret) ?: run {
            clearSignatureHelpOverlay()
            return
        }
        val caretPos = session.caretPosition
        val codeLeft = gutterWidthPx + dp(CODE_PADDING_DP)
        val anchorX = codeLeft + caretPos.column * charWidthPx - scrollXpx
        val anchorY = caretPos.line * lineHeightPx - scrollYpx
        onSignatureHelpOverlay?.invoke(SignatureHelpOverlay(help, anchorX, anchorY))
        handler.removeCallbacks(signatureHelpHideRunnable)
        handler.postDelayed(signatureHelpHideRunnable, SIGNATURE_HELP_VISIBLE_MS)
    }
    private fun dismissSignatureHelp() {
        signatureHelpDismissed = true
        clearSignatureHelpOverlay()
    }
    private fun afterTextEditTriggers(session: EditorSession, typedChar: Char? = null, isBackspace: Boolean = false) {
        onDiagnosticMessage?.invoke(null)
        if (completionSuppressNext) {
            completionSuppressNext = false
            if (typedChar != null && typedChar != '(' && typedChar != ',' &&
                (typedChar.isLetterOrDigit() || typedChar == '_')
            ) {
                dismissCompletion()
                return
            }
        }
        when {
            session.language == EditorLanguage.Xml ->
                if (typedChar != null && completionController.shouldAutoPopup(session, typedChar)) scheduleCompletion() else dismissCompletion()
            typedChar == '.' -> scheduleCompletion()
            typedChar != null && (typedChar.isLetterOrDigit() || typedChar == '_') ->
                if (completionController.shouldAutoPopup(session, typedChar)) scheduleCompletion() else dismissCompletion()
            isBackspace && completionActive -> scheduleCompletion()
            else -> dismissCompletion()
        }
        if (typedChar == '(' || typedChar == ',') armSignatureHelp() else syncSignatureHelpNow()
    }
    private fun clipboard(): ClipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private fun copySelection() {
        val session = session ?: return
        val sel = session.selection
        if (sel.isCollapsed) return
        clipboard().setPrimaryClip(ClipData.newPlainText("code", session.document.substring(sel.start, sel.end)))
    }
    private fun cutSelection() {
        val session = session ?: return
        val sel = session.selection
        if (sel.isCollapsed) return
        clipboard().setPrimaryClip(ClipData.newPlainText("code", session.document.substring(sel.start, sel.end)))
        edit { session.replaceRange(sel.start, sel.end, "", caret = sel.start) }
    }
    private fun pasteClipboard() {
        val session = session ?: return
        val clip = clipboard().primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(context)?.toString() ?: return
        val sel = session.selection
        edit { session.replaceRange(sel.start, sel.end, text, caret = sel.start + text.length) }
    }
    private inline fun edit(block: () -> Unit) {
        val session = session ?: return
        val revisionBefore = session.revision
        val textBefore = session.text
        block()
        val textChanged = session.revision != revisionBefore
        if (textChanged) {
            updateGutterWidth()
            recomputeMaxLineChars()
            session.diagnostics = shiftDiagnostics(session.diagnostics, textBefore, session.text)
            rebuildDiagLines(session)
            scheduleAnalysis()
            if (findQuery.isNotEmpty()) recomputeFindMatches()
            onEdited?.invoke()
        }
        afterCaretChange()
    }
    private fun afterCaretChange() {
        resetBlink()
        ensureCaretVisibleAnimated()
        invalidate()
        session?.caretPosition?.let { onCaretMoved?.invoke(it.line, it.column) }
        syncSignatureHelpNow()
    }
    private fun scheduleAnalysis() {
        handler.removeCallbacks(analysisRunnable)
        handler.postDelayed(analysisRunnable, ANALYSIS_DEBOUNCE_MS)
    }
    private fun runAnalysis() {
        val session = session ?: return
        handler.removeCallbacks(analysisRunnable)
        val diagnostics = analyzer?.invoke(session) ?: emptyList()
        session.diagnostics = diagnostics
        rebuildDiagLines(session)
        onDiagnostics?.invoke(diagnostics)
        invalidate()
    }
    private fun rebuildDiagLines(session: EditorSession) {
        if (session.diagnostics.isEmpty()) {
            diagLineSeverity = emptyMap()
            diagChipByLine = emptyMap()
            chipHitRects.clear()
            return
        }
        val doc = session.document
        val map = HashMap<Int, DiagnosticSeverity>()
        val chips = HashMap<Int, Diagnostic>()
        for (d in session.diagnostics) {
            val line = doc.offsetToPosition(d.start.coerceIn(0, doc.length)).line
            val prev = map[line]
            if (prev == null || severityRank(d.severity) > severityRank(prev)) map[line] = d.severity
            if (d.severity == DiagnosticSeverity.Error || d.severity == DiagnosticSeverity.Warning) {
                val cur = chips[line]
                if (cur == null || severityRank(d.severity) > severityRank(cur.severity)) chips[line] = d
            }
        }
        diagLineSeverity = map
        diagChipByLine = chips
    }
    private fun recomputeFindMatches() {
        val session = session
        if (session == null || findQuery.isEmpty()) {
            findMatches = emptyList()
            return
        }
        val text = session.text
        val matches = ArrayList<Int>()
        var i = text.indexOf(findQuery, ignoreCase = true)
        while (i >= 0) {
            matches.add(i)
            i = text.indexOf(findQuery, i + 1, ignoreCase = true)
        }
        findMatches = matches
    }
    private fun scrollCurrentFindMatchIntoView() {
        val session = session ?: return
        val idx = findCurrentIndex - 1
        val start = findMatches.getOrNull(idx) ?: return
        val pos = session.document.offsetToPosition(start)
        val targetTop = pos.line * lineHeightPx
        val (tx, ty) = targetScrollFor(pos.column, targetTop)
        animateVisualTo(tx, ty, targetTop)
    }
    private fun ensureCaretVisibleAnimated() {
        val session = session ?: return
        val caret = session.caretPosition
        val caretTop = caret.line * lineHeightPx
        val (tx, ty) = targetScrollFor(caret.column, caretTop)
        animateVisualTo(tx, ty, caretTop)
    }
    private fun targetScrollFor(column: Int, lineTop: Float): Pair<Float, Float> {
        val viewHeight = viewportHeightPx()
        val margin = lineHeightPx * 0.75f
        var ty = scrollYpx
        val bottom = lineTop + lineHeightPx
        if (lineTop < scrollYpx + margin) ty = (lineTop - margin).coerceAtLeast(0f)
        else if (bottom > scrollYpx + viewHeight - margin) ty = bottom - viewHeight + margin
        var tx = scrollXpx
        val codeViewport = width - gutterWidthPx - dp(CODE_PADDING_DP)
        val caretX = column * charWidthPx
        if (caretX < scrollXpx) tx = caretX
        else if (caretX > scrollXpx + codeViewport) tx = caretX - codeViewport + charWidthPx
        return clampScrollX(tx) to clampScrollY(ty)
    }
    private fun viewportHeightPx(): Float = max(0f, height.toFloat())
    private fun animateVisualTo(tx: Float, ty: Float, activeTop: Float) {
        val dx = tx - scrollXpx
        val dy = ty - scrollYpx
        val da = activeTop - renderedActiveLineTop
        if (abs(dx) < 1f && abs(dy) < 1f && abs(da) < 1f) {
            scrollXpx = tx; scrollYpx = ty; renderedActiveLineTop = activeTop
            invalidate()
            return
        }
        animStartX = scrollXpx; animStartY = scrollYpx; animStartActive = renderedActiveLineTop
        animTargetX = tx; animTargetY = ty; animTargetActive = activeTop
        visualAnimator.cancel()
        visualAnimator.start()
    }
    private fun clampScroll() {
        scrollYpx = clampScrollY(scrollYpx)
        scrollXpx = clampScrollX(scrollXpx)
    }
    private fun clampScrollY(value: Float): Float {
        val session = session ?: return 0f
        val contentHeight = session.lineCount * lineHeightPx + bottomScrollPaddingPx
        return value.coerceIn(0f, max(0f, contentHeight - viewportHeightPx()))
    }
    private fun clampScrollX(value: Float): Float {
        val contentWidth = maxLineChars * charWidthPx + dp(CODE_PADDING_DP) * 2
        val codeViewport = width - gutterWidthPx
        return value.coerceIn(0f, max(0f, contentWidth - codeViewport))
    }
    private fun recomputeMaxLineChars() {
        val session = session ?: return
        var maxLen = 0
        val doc = session.document
        for (line in 0 until doc.lineCount) {
            val len = doc.lineEndOffset(line) - doc.lineStartOffset(line)
            if (len > maxLen) maxLen = len
        }
        maxLineChars = maxLen
    }
    private fun invalidateCaret() {
        val session = session ?: return
        if (lineHeightPx <= 0f) return
        val caret = session.caretPosition
        val codeLeft = gutterWidthPx + dp(CODE_PADDING_DP)
        val x = codeLeft + caret.column * charWidthPx - scrollXpx
        val top = caret.line * lineHeightPx - scrollYpx
        invalidate((x - dp(2f)).toInt(), top.toInt(), (x + dp(CARET_W_DP) + dp(2f)).toInt(), (top + lineHeightPx).toInt())
    }
    private fun focusAndShowKeyboard() {
        if (!isFocused) requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(this, 0)
    }
    private var activeInputConnection: CodeInputConnection? = null
    private fun syncImeSelection() {
        val session = session ?: return
        val sel = session.selection
        post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.updateSelection(this, sel.start, sel.end, -1, -1)
        }
    }
    private fun dp(value: Float): Float = value * densityScale
    private inner class CodeInputConnection : BaseInputConnection(this@CodeEditorView, false) {
        private var composingStart = -1
        private var composingEnd = -1
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val session = session ?: return false
            var value = text?.toString() ?: return false
            if (value == ". ") value = "."
            if (value == " " && session.selection.caret > 0 &&
                session.document.charAt(session.selection.caret - 1) == '.'
            ) {
                return true
            }
            if (value == "\n" && completionActive) {
                if (shouldAcceptCompletionOnEnter()) {
                    acceptSelectedCompletion()
                } else {
                    dismissCompletion()
                    edit { SmartEdit.typeChar(session, '\n', tabSize) }
                    afterTextEditTriggers(session, typedChar = '\n')
                }
                return true
            }
            if (composingStart >= 0) {
                val s = composingStart
                val e = composingEnd
                composingStart = -1; composingEnd = -1
                edit { session.replaceRange(s, e, value, caret = s + value.length) }
            } else if (value.length == 1) {
                val c = value[0]
                edit { SmartEdit.typeChar(session, c, tabSize) }
                afterTextEditTriggers(session, typedChar = c)
            } else {
                edit { SmartEdit.type(session, value, tabSize) }
                dismissCompletion()
            }
            return true
        }
        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val session = session ?: return false
            val value = text?.toString() ?: return false
            if ('\n' in value) return false
            val sel = session.selection
            val start = if (composingStart >= 0) composingStart else sel.start
            val end = if (composingStart >= 0) composingEnd else sel.end
            edit { session.replaceRange(start, end, value, caret = start + value.length) }
            composingStart = start
            composingEnd = start + value.length
            afterTextEditTriggers(session, typedChar = value.lastOrNull())
            return true
        }
        fun clearComposing() {
            composingStart = -1
            composingEnd = -1
        }
        override fun setComposingRegion(start: Int, end: Int): Boolean = false
        override fun finishComposingText(): Boolean {
            composingStart = -1
            composingEnd = -1
            return true
        }
        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            val session = session ?: return false
            val sel = session.selection
            if (sel.isCollapsed && beforeLength == 1 && afterLength == 0) {
                edit { SmartEdit.backspace(session, tabSize) }
                afterTextEditTriggers(session, isBackspace = true)
                return true
            }
            val len = session.document.length
            val start = (sel.start - beforeLength).coerceAtLeast(0)
            val end = (sel.end + afterLength).coerceAtMost(len)
            edit { session.replaceRange(start, end, "", caret = start) }
            return true
        }
        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN) return onKeyDown(event.keyCode, event)
            return super.sendKeyEvent(event)
        }
        override fun getTextBeforeCursor(length: Int, flags: Int): CharSequence = ""
        override fun getTextAfterCursor(length: Int, flags: Int): CharSequence = ""
        override fun getSelectedText(flags: Int): CharSequence? = null
        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText {
            val session = session ?: return ExtractedText()
            val sel = session.selection
            return ExtractedText().apply {
                text = ""
                startOffset = 0
                selectionStart = sel.start
                selectionEnd = sel.end
            }
        }
    }
    private companion object {
        const val LINE_HEIGHT_RATIO = 20f / 13f
        const val GUTTER_TEXT_RATIO = 12f / 13f
        const val CHIP_TEXT_RATIO = 11.5f / 13f
        const val BLINK_MS = 530L
        const val COMPLETION_DEBOUNCE_MS = 110L
        const val SIGNATURE_HELP_DEBOUNCE_MS = 40L
        const val SIGNATURE_HELP_VISIBLE_MS = 3500L
        const val ANALYSIS_DEBOUNCE_MS = 300L
        const val SCROLL_ANIM_MS = 140L
        const val CODE_PADDING_DP = 14f
        const val GUTTER_START_DP = 6f
        const val DOT_SIZE_DP = 8f
        const val GIT_BAR_W_DP = 3f
        const val GIT_BAR_H_DP = 16f
        const val CARET_W_DP = 2f
        const val HANDLE_RADIUS_DP = 6f
        fun severityRank(s: DiagnosticSeverity): Int = when (s) {
            DiagnosticSeverity.Error -> 3
            DiagnosticSeverity.Warning -> 2
            DiagnosticSeverity.Info -> 1
            DiagnosticSeverity.Hint -> 0
        }
        fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
        fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float = abs(x1 - x2) + abs(y1 - y2)
        fun lerp(a: Float, b: Float, f: Float): Float = a + (b - a) * f
        fun withAlpha(color: Int, alpha: Float): Int {
            val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
            return (a shl 24) or (color and 0x00FFFFFF)
        }
    }
}
