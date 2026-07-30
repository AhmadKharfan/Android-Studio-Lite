package com.ahmadkharfan.androidstudiolite.feature.editor.engine
object CallSiteScanner {
    data class CallSite(
        val openParenOffset: Int,
        val calleeName: String,
        val activeParameterIndex: Int,
        val suppliedNamedArgs: Set<String>,
        val editingNamedArgLabel: Boolean,
        val activeNamedArg: String?,
    )
    private data class Frame(
        val open: Char,
        val openOffset: Int,
        var commas: Int,
        val nameStart: Int,
        val nameEnd: Int,
    )

    fun enclosingCall(text: String, caret: Int): CallSite? {
        val end = caret.coerceIn(0, text.length)
        val stack = collectOpenFrames(text, end)
        return findEnclosingCall(text, caret, stack)
    }

    private fun collectOpenFrames(text: String, end: Int): List<Frame> {
        val stack = ArrayList<Frame>()
        var i = 0
        while (i < end) {
            i = when {
                text.startsWith("//", i) -> skipLineComment(text, i, end)
                text.startsWith("/*", i) -> skipBlockComment(text, i, end)
                text.startsWith("\"\"\"", i) -> skipRawString(text, i, end)
                text[i] == '"' || text[i] == '\'' -> skipQuotedLiteral(text, i, end)
                text[i] == '(' -> trackCallOpening(text, i, stack)
                text[i] == '[' -> trackBracketOpening('[', i, stack)
                text[i] == '{' -> trackBracketOpening('{', i, stack)
                text[i] == ')' || text[i] == ']' || text[i] == '}' -> closeBalancedFrame(i, stack)
                text[i] == ',' -> countArgumentSeparator(i, stack)
                else -> i + 1
            }
        }
        return stack
    }

    private fun skipLineComment(text: String, start: Int, end: Int): Int {
        var i = start + 2
        while (i < end && text[i] != '\n') i++
        return i
    }

    private fun skipBlockComment(text: String, start: Int, end: Int): Int {
        var i = start + 2
        while (i < end && !text.startsWith("*/", i)) i++
        return i + 2
    }

    private fun skipRawString(text: String, start: Int, end: Int): Int {
        var i = start + 3
        while (i < end && !text.startsWith("\"\"\"", i)) i++
        return i + 3
    }

    private fun skipQuotedLiteral(text: String, start: Int, end: Int): Int {
        val quote = text[start]
        var i = start + 1
        while (i < end && text[i] != quote) {
            if (text[i] == '\\') i++
            i++
        }
        return i + 1
    }

    private fun trackCallOpening(text: String, index: Int, stack: MutableList<Frame>): Int {
        val nameEnd = trimTrailingWhitespace(text, index)
        var nameStart = nameEnd
        while (nameStart > 0 && isNameChar(text[nameStart - 1])) nameStart--
        if (nameEnd > nameStart && !KotlinLexUtil.isDeclarationBeforeParen(text, nameStart)) {
            stack.add(Frame('(', index, 0, nameStart, nameEnd))
        }
        return index + 1
    }

    private fun trackBracketOpening(open: Char, index: Int, stack: MutableList<Frame>): Int {
        stack.add(Frame(open, index, 0, index, index))
        return index + 1
    }

    private fun closeBalancedFrame(index: Int, stack: MutableList<Frame>): Int {
        if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
        return index + 1
    }

    private fun countArgumentSeparator(index: Int, stack: MutableList<Frame>): Int {
        stack.lastOrNull()?.commas = (stack.lastOrNull()?.commas ?: 0) + 1
        return index + 1
    }

    private fun findEnclosingCall(text: String, caret: Int, stack: List<Frame>): CallSite? {
        for (idx in stack.indices.reversed()) {
            val frame = stack[idx]
            if (frame.open == '(' && frame.nameEnd > frame.nameStart) {
                val callee = text.substring(frame.nameStart, frame.nameEnd)
                val supplied = parseSuppliedNamedArgs(text, frame.openOffset, caret)
                val segment = currentArgSegment(text, frame.openOffset, caret)
                val editingLabel = isEditingNamedArgLabel(text, segment, caret)
                val activeNamed = activeNamedArgName(text, segment)
                return CallSite(
                    openParenOffset = frame.openOffset,
                    calleeName = callee,
                    activeParameterIndex = frame.commas,
                    suppliedNamedArgs = supplied,
                    editingNamedArgLabel = editingLabel,
                    activeNamedArg = activeNamed,
                )
            }
            if (frame.open != '(') return null
        }
        return null
    }

    private fun parseSuppliedNamedArgs(text: String, openParen: Int, caret: Int): Set<String> {
        val out = LinkedHashSet<String>()
        var i = openParen + 1
        var depth = 0
        while (i < caret) {
            when (text[i]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
                else -> if (depth == 0) {
                    val name = readNamedArgAt(text, i, caret)
                    if (name != null) {
                        out.add(name)
                        i = skipNamedArgValue(text, i, caret)
                        continue
                    }
                }
            }
            i++
        }
        return out
    }
    private fun readNamedArgAt(text: String, start: Int, limit: Int): String? {
        var i = start
        while (i < limit && text[i].isWhitespace()) i++
        val nameStart = i
        while (i < limit && (text[i].isLetterOrDigit() || text[i] == '_')) i++
        if (i == nameStart) return null
        var j = i
        while (j < limit && text[j].isWhitespace()) j++
        if (j < limit && text[j] == '=') return text.substring(nameStart, i)
        return null
    }
    private fun skipNamedArgValue(text: String, start: Int, limit: Int): Int {
        var i = start
        while (i < limit && text[i] != '=') i++
        if (i >= limit) return limit
        i++
        var depth = 0
        while (i < limit) {
            when (text[i]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> if (depth == 0) return i else depth--
                ',' -> if (depth == 0) return i
                '"' -> {
                    i++
                    while (i < limit && text[i] != '"') {
                        if (text[i] == '\\') i++
                        i++
                    }
                }
            }
            i++
        }
        return limit
    }
    private fun currentArgSegment(text: String, openParen: Int, caret: Int): IntRange {
        var depth = 0
        var segmentStart = openParen + 1
        var i = openParen + 1
        while (i < caret) {
            when (text[i]) {
                '(', '[', '{' -> depth++
                ')' -> if (depth == 0) return segmentStart until i else depth--
                ',' -> if (depth == 0) segmentStart = i + 1
            }
            i++
        }
        return segmentStart until caret
    }
    private fun isEditingNamedArgLabel(text: String, segment: IntRange, caret: Int): Boolean {
        if (segment.isEmpty()) return false
        val slice = text.substring(segment.first, (segment.last + 1).coerceAtMost(text.length))
        val eq = slice.indexOf('=')
        if (eq < 0) return false
        val caretInSegment = (caret - segment.first).coerceIn(0, slice.length)
        return caretInSegment <= eq
    }
    private fun activeNamedArgName(text: String, segment: IntRange): String? {
        if (segment.isEmpty()) return null
        val slice = text.substring(segment.first, segment.last + 1)
        val eq = slice.indexOf('=')
        if (eq < 0) return null
        val name = slice.substring(0, eq).trim()
        return name.ifEmpty { null }
    }
    private fun trimTrailingWhitespace(text: String, parenIndex: Int): Int {
        var j = parenIndex
        while (j > 0 && text[j - 1].isWhitespace()) j--
        return j
    }
    private fun isNameChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '.' || c == '$'
}
