package com.ahmadkharfan.androidstudiolite.feature.editor.engine
data class TextPosition(val line: Int, val column: Int)
class EditorDocument(initial: String = "") {
    private val buffer = TextBuffer(initial)
    private val lineStarts = ArrayList<Int>()
    init {
        rebuildLineStarts()
    }
    val length: Int get() = buffer.length
    val lineCount: Int get() = lineStarts.size
    val text: String get() = buffer.toString()
    fun charAt(index: Int): Char = buffer.charAt(index)
    fun substring(start: Int, end: Int): String = buffer.substring(start, end)
    fun lineOfOffset(offset: Int): Int = lineIndexOf(offset.coerceIn(0, length))
    fun lineStartOffset(line: Int): Int = lineStarts[line.coerceIn(0, lineCount - 1)]
    fun lineEndOffset(line: Int): Int {
        val l = line.coerceIn(0, lineCount - 1)
        return if (l + 1 < lineStarts.size) lineStarts[l + 1] - 1 else length
    }
    fun lineText(line: Int): String = substring(lineStartOffset(line), lineEndOffset(line))
    fun offsetToPosition(offset: Int): TextPosition {
        val o = offset.coerceIn(0, length)
        val line = lineIndexOf(o)
        return TextPosition(line, o - lineStarts[line])
    }
    fun positionToOffset(line: Int, column: Int): Int {
        val l = line.coerceIn(0, lineCount - 1)
        val base = lineStarts[l]
        val maxColumn = lineEndOffset(l) - base
        return base + column.coerceIn(0, maxColumn)
    }
    fun replaceRange(start: Int, end: Int, newText: String): String {
        val s = start.coerceIn(0, length)
        val e = end.coerceIn(s, length)
        val removed = buffer.substring(s, e)
        updateLineStarts(s, e, newText)
        buffer.replace(s, e, newText)
        return removed
    }
    private fun updateLineStarts(start: Int, end: Int, newText: String) {
        val delta = newText.length - (end - start)
        val firstLine = lineIndexOf(start)
        val lastLine = lineIndexOf(end)
        val rebuilt = ArrayList<Int>(firstLine + 1 + lineStarts.size - lastLine)
        for (i in 0..firstLine) rebuilt.add(lineStarts[i])
        for (j in newText.indices) if (newText[j] == '\n') rebuilt.add(start + j + 1)
        for (i in lastLine + 1 until lineStarts.size) rebuilt.add(lineStarts[i] + delta)
        lineStarts.clear()
        lineStarts.addAll(rebuilt)
    }
    private fun rebuildLineStarts() {
        lineStarts.clear()
        lineStarts.add(0)
        val n = buffer.length
        for (i in 0 until n) if (buffer.charAt(i) == '\n') lineStarts.add(i + 1)
    }
    private fun lineIndexOf(offset: Int): Int {
        var lo = 0
        var hi = lineStarts.size - 1
        var answer = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lineStarts[mid] <= offset) {
                answer = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return answer
    }
}
