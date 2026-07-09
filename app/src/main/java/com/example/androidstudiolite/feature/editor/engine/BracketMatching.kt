package com.example.androidstudiolite.feature.editor.engine
private const val OPENERS = "([{"
private const val CLOSERS = ")]}"
private const val SCAN_LIMIT = 20_000
fun bracketMatchAt(document: EditorDocument, caret: Int): Pair<Int, Int>? {
    for (pos in intArrayOf(caret - 1, caret)) {
        if (pos < 0 || pos >= document.length) continue
        val c = document.charAt(pos)
        val openIndex = OPENERS.indexOf(c)
        if (openIndex >= 0) {
            val match = scan(document, pos, +1, c, CLOSERS[openIndex])
            if (match >= 0) return pos to match
        }
        val closeIndex = CLOSERS.indexOf(c)
        if (closeIndex >= 0) {
            val match = scan(document, pos, -1, c, OPENERS[closeIndex])
            if (match >= 0) return pos to match
        }
    }
    return null
}
private fun scan(document: EditorDocument, from: Int, step: Int, same: Char, target: Char): Int {
    var depth = 0
    var i = from
    var guard = 0
    while (i in 0 until document.length && guard < SCAN_LIMIT) {
        val c = document.charAt(i)
        if (c == same) depth++ else if (c == target) {
            depth--
            if (depth == 0) return i
        }
        i += step
        guard++
    }
    return -1
}
