package com.ahmadkharfan.androidstudiolite.data.ai.agent

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
        for (c in chunk) consumeChar(c)
    }

    private fun consumeChar(c: Char) {
        when (mode) {
            Mode.BOOTSTRAP -> consumeBootstrap(c)
            Mode.FENCE -> consumeFence(c)
            Mode.OBJECT -> consumeObject(c)
            Mode.KEY -> consumeKey(c)
            Mode.AFTER_KEY -> consumeAfterKey(c)
            Mode.BEFORE_VALUE -> consumeBeforeValue(c)
            Mode.VALUE_STRING -> consumeValueString(c)
            Mode.ESCAPE -> consumeEscape(c)
            Mode.UNICODE -> consumeUnicode(c)
            Mode.PROSE -> consumeProse(c)
        }
    }

    private fun consumeBootstrap(c: Char) {
        when {
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
    }

    private fun consumeFence(c: Char) {
        when (c) {
            '{' -> {
                depth = 1
                mode = Mode.OBJECT
            }
            else -> Unit
        }
    }

    private fun consumeProse(c: Char) {
        onFinal(c.toString())
    }

    private fun consumeObject(c: Char) {
        when (c) {
            '{' -> depth++
            '}' -> depth = (depth - 1).coerceAtLeast(0)
            '[', ']' -> Unit
            '"' -> {
                key = StringBuilder()
                mode = Mode.KEY
            }
        }
    }

    private fun consumeKey(c: Char) {
        when (c) {
            '\\' -> {
                returnMode = Mode.KEY
                mode = Mode.ESCAPE
            }
            '"' -> mode = Mode.AFTER_KEY
            else -> key.append(c)
        }
    }

    private fun consumeAfterKey(c: Char) {
        when {
            c.isWhitespace() -> Unit
            c == ':' -> mode = Mode.BEFORE_VALUE
            else -> mode = Mode.OBJECT
        }
    }

    private fun consumeBeforeValue(c: Char) {
        when {
            c.isWhitespace() -> Unit
            c == '"' -> beginStringValue()
            c == '{' -> {
                depth++
                mode = Mode.OBJECT
            }
            c == ',' -> mode = Mode.OBJECT
            c == '}' -> {
                depth = (depth - 1).coerceAtLeast(0)
                mode = Mode.OBJECT
            }
            else -> Unit
        }
    }

    private fun beginStringValue() {
        val field = key.toString()
        captureField = if (depth == 1 && (field == "thought" || field == "final")) field else null
        mode = Mode.VALUE_STRING
    }

    private fun consumeValueString(c: Char) {
        when (c) {
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
    }

    private fun consumeEscape(c: Char) {
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

    private fun consumeUnicode(c: Char) {
        unicodeBuf.append(c)
        if (unicodeBuf.length == 4) {
            val ch = (unicodeBuf.toString().toIntOrNull(16) ?: 0).toChar()
            if (returnMode == Mode.KEY) key.append(ch)
            else emit(ch)
            mode = returnMode
        }
    }

    private fun emit(c: Char) {
        when (captureField) {
            "thought" -> onThought(c.toString())
            "final" -> onFinal(c.toString())
        }
    }
}
