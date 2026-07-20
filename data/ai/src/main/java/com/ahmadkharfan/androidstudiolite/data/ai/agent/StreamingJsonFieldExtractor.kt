package com.ahmadkharfan.androidstudiolite.data.ai.agent

/**
 * Incrementally extracts decoded text deltas for top-level JSON string fields `"thought"` and
 * `"final"` as raw model tokens arrive. If the stream does not begin with `{`, the entire stream is
 * treated as `final` prose so non-JSON replies still stream live.
 */
class StreamingJsonFieldExtractor(
    private val onThought: (String) -> Unit,
    private val onFinal: (String) -> Unit,
) {
    private enum class Mode {
        BOOTSTRAP,
        FENCE,
        OBJECT,
        KEY,
        AFTER_KEY,
        BEFORE_VALUE,
        VALUE_STRING,
        ESCAPE,
        UNICODE,
        PROSE,
    }

    private var mode = Mode.BOOTSTRAP
    private var returnMode = Mode.OBJECT
    private var depth = 0
    private var key = StringBuilder()
    private var captureField: String? = null
    private var unicodeBuf = StringBuilder()

    fun feed(chunk: String) {
        if (chunk.isEmpty()) return
        for (c in chunk) process(c)
    }

    private fun process(c: Char) {
        when (mode) {
            Mode.BOOTSTRAP -> when {
                c.isWhitespace() -> Unit
                c == '`' -> mode = Mode.FENCE
                c == '{' -> {
                    depth = 1
                    mode = Mode.OBJECT
                }
                else -> {
                    mode = Mode.PROSE
                    onFinal(c.toString())
                }
            }
            Mode.FENCE -> when (c) {
                '{' -> {
                    depth = 1
                    mode = Mode.OBJECT
                }
                else -> Unit
            }
            Mode.PROSE -> onFinal(c.toString())
            Mode.OBJECT -> when (c) {
                '{' -> depth++
                '}' -> depth = (depth - 1).coerceAtLeast(0)
                '[', ']' -> Unit
                '"' -> {
                    key = StringBuilder()
                    mode = Mode.KEY
                }
            }
            Mode.KEY -> when (c) {
                '\\' -> {
                    returnMode = Mode.KEY
                    mode = Mode.ESCAPE
                }
                '"' -> mode = Mode.AFTER_KEY
                else -> key.append(c)
            }
            Mode.AFTER_KEY -> when {
                c.isWhitespace() -> Unit
                c == ':' -> mode = Mode.BEFORE_VALUE
                else -> mode = Mode.OBJECT
            }
            Mode.BEFORE_VALUE -> when {
                c.isWhitespace() -> Unit
                c == '"' -> {
                    val field = key.toString()
                    // Only top-level thought/final (depth == 1) are streamed to the UI.
                    captureField = if (depth == 1 && (field == "thought" || field == "final")) field else null
                    mode = Mode.VALUE_STRING
                }
                c == '{' -> {
                    depth++
                    mode = Mode.OBJECT
                }
                c == ',' -> mode = Mode.OBJECT
                c == '}' -> {
                    depth = (depth - 1).coerceAtLeast(0)
                    mode = Mode.OBJECT
                }
                // Skip non-string scalars / arrays until comma or end.
                else -> Unit
            }
            Mode.VALUE_STRING -> when (c) {
                '\\' -> {
                    returnMode = Mode.VALUE_STRING
                    mode = Mode.ESCAPE
                }
                '"' -> {
                    captureField = null
                    mode = Mode.OBJECT
                }
                else -> emit(c)
            }
            Mode.ESCAPE -> {
                val decoded = when (c) {
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    '"' -> '"'
                    '\\' -> '\\'
                    '/' -> '/'
                    'b' -> '\b'
                    'f' -> '\u000c'
                    'u' -> {
                        unicodeBuf = StringBuilder()
                        mode = Mode.UNICODE
                        return
                    }
                    else -> c
                }
                if (returnMode == Mode.KEY) key.append(decoded)
                else emit(decoded)
                mode = returnMode
            }
            Mode.UNICODE -> {
                unicodeBuf.append(c)
                if (unicodeBuf.length == 4) {
                    val ch = (unicodeBuf.toString().toIntOrNull(16) ?: 0).toChar()
                    if (returnMode == Mode.KEY) key.append(ch)
                    else emit(ch)
                    mode = returnMode
                }
            }
        }
    }

    private fun emit(c: Char) {
        when (captureField) {
            "thought" -> onThought(c.toString())
            "final" -> onFinal(c.toString())
        }
    }
}
