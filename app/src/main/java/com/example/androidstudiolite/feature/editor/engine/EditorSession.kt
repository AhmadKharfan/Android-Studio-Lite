package com.example.androidstudiolite.feature.editor.engine
data class Selection(val anchor: Int, val caret: Int) {
    val start: Int get() = minOf(anchor, caret)
    val end: Int get() = maxOf(anchor, caret)
    val isCollapsed: Boolean get() = anchor == caret
    val length: Int get() = end - start
    fun coerceIn(max: Int): Selection = Selection(anchor.coerceIn(0, max), caret.coerceIn(0, max))
    companion object {
        fun collapsed(offset: Int): Selection = Selection(offset, offset)
    }
}
class EditorSession(
    initialText: String = "",
    val language: EditorLanguage = EditorLanguage.Plain,
    val filePath: String = "",
) {
    val document = EditorDocument(initialText)
    private val history = EditHistory()
    private val lexer = SyntaxLexer.forLanguage(language)
    private val lineTokens = ArrayList<List<SyntaxToken>>()
    private val lineEndStates = ArrayList<LexerState>()
    var selection: Selection = Selection.collapsed(0)
        private set
    var revision: Int = 0
        private set
    var diagnostics: List<Diagnostic> = emptyList()
    var onChange: (() -> Unit)? = null
    init {
        tokenizeAll()
    }
    val text: String get() = document.text
    val lineCount: Int get() = document.lineCount
    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo
    val caretPosition: TextPosition get() = document.offsetToPosition(selection.caret)
    fun tokensForLine(line: Int): List<SyntaxToken> = lineTokens.getOrElse(line) { emptyList() }
    fun setSelection(anchor: Int, caret: Int) {
        val max = document.length
        selection = Selection(anchor.coerceIn(0, max), caret.coerceIn(0, max))
    }
    fun setCaret(offset: Int) = setSelection(offset, offset)
    fun selectAll() = setSelection(0, document.length)
    fun replaceRange(
        start: Int,
        end: Int,
        text: String,
        caret: Int = start + text.length,
        coalesce: Boolean = false,
    ) {
        val s = start.coerceIn(0, document.length)
        val e = end.coerceIn(s, document.length)
        val before = selection
        val removed = applyEdit(s, e, text)
        val after = Selection.collapsed(caret.coerceIn(0, document.length))
        selection = after
        history.record(EditRecord(s, removed, text, before, after), coalesce)
        onChange?.invoke()
    }
    fun undo(): Boolean {
        val record = history.undo() ?: return false
        applyEdit(record.start, record.start + record.inserted.length, record.removed)
        selection = record.caretBefore.coerceIn(document.length)
        onChange?.invoke()
        return true
    }
    fun redo(): Boolean {
        val record = history.redo() ?: return false
        applyEdit(record.start, record.start + record.removed.length, record.inserted)
        selection = record.caretAfter.coerceIn(document.length)
        onChange?.invoke()
        return true
    }
    private fun applyEdit(start: Int, end: Int, text: String): String {
        val removed = document.replaceRange(start, end, text)
        val firstLine = document.lineOfOffset(start)
        val lastChangedLine = document.lineOfOffset(start + text.length)
        retokenizeFrom(firstLine, lastChangedLine)
        revision++
        return removed
    }
    private fun tokenizeAll() {
        lineTokens.clear()
        lineEndStates.clear()
        var state = lexer.initialState
        for (line in 0 until document.lineCount) {
            val result = lexer.tokenizeLine(document.lineText(line), state)
            lineTokens.add(result.tokens)
            lineEndStates.add(result.endState)
            state = result.endState
        }
    }
    private fun retokenizeFrom(firstLine: Int, lastChangedLine: Int) {
        val oldTokens = ArrayList(lineTokens)
        val oldEndStates = ArrayList(lineEndStates)
        val oldCount = oldEndStates.size
        val newCount = document.lineCount
        val delta = newCount - oldCount
        while (lineTokens.size > firstLine) {
            lineTokens.removeAt(lineTokens.lastIndex)
            lineEndStates.removeAt(lineEndStates.lastIndex)
        }
        var i = firstLine
        var entry = if (firstLine == 0) lexer.initialState else lineEndStates[firstLine - 1]
        while (i < newCount) {
            val oldIndex = i - delta
            val converged = i > lastChangedLine && oldIndex in 0 until oldCount &&
                entry == (if (oldIndex == 0) lexer.initialState else oldEndStates[oldIndex - 1])
            if (converged) {
                var k = oldIndex
                while (i < newCount) {
                    lineTokens.add(oldTokens[k])
                    lineEndStates.add(oldEndStates[k])
                    i++
                    k++
                }
                break
            }
            val result = lexer.tokenizeLine(document.lineText(i), entry)
            lineTokens.add(result.tokens)
            lineEndStates.add(result.endState)
            entry = result.endState
            i++
        }
    }
}
