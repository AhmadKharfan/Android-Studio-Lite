package com.ahmadkharfan.androidstudiolite.feature.editor.engine
class TextBuffer(initial: String = "") {
    private var chars: CharArray
    private var gapStart: Int
    private var gapEnd: Int
    init {
        val capacity = maxOf(initial.length + MIN_GAP, MIN_GAP)
        chars = CharArray(capacity)
        initial.toCharArray(chars, destinationOffset = 0, startIndex = 0, endIndex = initial.length)
        gapStart = initial.length
        gapEnd = capacity
    }
    val length: Int get() = chars.size - (gapEnd - gapStart)
    fun charAt(index: Int): Char {
        require(index in 0 until length) { "index $index out of bounds [0, $length)" }
        return if (index < gapStart) chars[index] else chars[index + (gapEnd - gapStart)]
    }
    fun substring(start: Int, end: Int): String {
        require(start in 0..end && end <= length) { "range [$start, $end) out of [0, $length)" }
        if (start == end) return ""
        val out = CharArray(end - start)
        for (i in start until end) out[i - start] = charAt(i)
        return String(out)
    }
    fun replace(start: Int, end: Int, text: String) {
        require(start in 0..end && end <= length) { "range [$start, $end) out of [0, $length)" }
        moveGapTo(start)
        gapEnd += (end - start)
        ensureGap(text.length)
        for (element in text) chars[gapStart++] = element
    }
    override fun toString(): String = substring(0, length)
    private fun moveGapTo(pos: Int) {
        when {
            pos == gapStart -> return
            pos < gapStart -> {
                val count = gapStart - pos
                chars.copyInto(chars, destinationOffset = gapEnd - count, startIndex = pos, endIndex = gapStart)
                gapStart = pos
                gapEnd -= count
            }
            else -> {
                val count = pos - gapStart
                chars.copyInto(chars, destinationOffset = gapStart, startIndex = gapEnd, endIndex = gapEnd + count)
                gapStart += count
                gapEnd += count
            }
        }
    }
    private fun ensureGap(needed: Int) {
        val gap = gapEnd - gapStart
        if (gap >= needed) return
        val extra = maxOf(needed - gap, MIN_GAP)
        val newCapacity = chars.size + extra
        val grown = CharArray(newCapacity)
        chars.copyInto(grown, destinationOffset = 0, startIndex = 0, endIndex = gapStart)
        val suffixLength = chars.size - gapEnd
        chars.copyInto(grown, destinationOffset = newCapacity - suffixLength, startIndex = gapEnd, endIndex = chars.size)
        chars = grown
        gapEnd = newCapacity - suffixLength
    }
    private companion object {
        const val MIN_GAP = 64
    }
}
