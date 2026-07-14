package com.ahmadkharfan.androidstudiolite.tooling.proto

/**
 * A tiny, dependency-free JSON tree + codec. Both the app (client) and the tooling server exchange
 * newline-delimited JSON over stdio, and both must parse/emit it without pulling in a serialization
 * library (the server is a plain-JVM fat jar; the app already avoids `org.json` because it can't run
 * in host unit tests). This is deliberately minimal — just objects, arrays, strings, numbers, bools
 * and null — which is all the JSON-RPC protocol here needs.
 */
sealed interface JsonValue {

    data class Obj(val fields: Map<String, JsonValue>) : JsonValue {
        operator fun get(key: String): JsonValue? = fields[key]
        fun string(key: String): String? = (fields[key] as? Str)?.value
        fun int(key: String): Int? = (fields[key] as? Num)?.value?.toInt()
        fun long(key: String): Long? = (fields[key] as? Num)?.value?.toLong()
        fun bool(key: String): Boolean? = (fields[key] as? Bool)?.value
        fun obj(key: String): Obj? = fields[key] as? Obj
        fun array(key: String): Arr? = fields[key] as? Arr
    }

    data class Arr(val items: List<JsonValue>) : JsonValue

    data class Str(val value: String) : JsonValue

    data class Num(val value: Double) : JsonValue

    data class Bool(val value: Boolean) : JsonValue

    data object Null : JsonValue

    fun encode(): String = StringBuilder().also { write(it) }.toString()

    private fun write(sb: StringBuilder) {
        when (this) {
            is Obj -> {
                sb.append('{')
                var first = true
                for ((k, v) in fields) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(sb, k); sb.append(':'); v.write(sb)
                }
                sb.append('}')
            }
            is Arr -> {
                sb.append('[')
                items.forEachIndexed { i, v -> if (i > 0) sb.append(','); v.write(sb) }
                sb.append(']')
            }
            is Str -> writeString(sb, value)
            is Num -> {
                // Emit integral doubles without a trailing ".0" so ids/line numbers stay clean.
                if (value == value.toLong().toDouble()) sb.append(value.toLong()) else sb.append(value)
            }
            is Bool -> sb.append(if (value) "true" else "false")
            Null -> sb.append("null")
        }
    }

    companion object {
        fun of(value: String?): JsonValue = if (value == null) Null else Str(value)
        fun of(value: Int): JsonValue = Num(value.toDouble())
        fun of(value: Long): JsonValue = Num(value.toDouble())
        fun of(value: Boolean): JsonValue = Bool(value)

        fun obj(vararg pairs: Pair<String, JsonValue>): Obj = Obj(linkedMapOf(*pairs))
        fun arr(items: List<JsonValue>): Arr = Arr(items)

        fun parse(text: String): JsonValue = Parser(text).parse()

        private fun writeString(sb: StringBuilder, s: String) {
            sb.append('"')
            for (c in s) {
                when (c) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    '\b' -> sb.append("\\b")
                    '' -> sb.append("\\f")
                    else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
                }
            }
            sb.append('"')
        }
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun parse(): JsonValue {
            val v = parseValue()
            skipWs()
            return v
        }

        private fun parseValue(): JsonValue {
            skipWs()
            if (i >= s.length) throw IllegalArgumentException("Unexpected end of JSON")
            return when (s[i]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> Str(parseString())
                't', 'f' -> parseBool()
                'n' -> parseNull()
                else -> Num(parseNumber())
            }
        }

        private fun parseObject(): Obj {
            val map = LinkedHashMap<String, JsonValue>()
            expect('{'); skipWs()
            if (peek() == '}') { i++; return Obj(map) }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs(); expect(':')
                map[key] = parseValue()
                skipWs()
                when (peek()) {
                    ',' -> { i++; continue }
                    '}' -> { i++; break }
                    else -> throw IllegalArgumentException("Expected ',' or '}' at $i")
                }
            }
            return Obj(map)
        }

        private fun parseArray(): Arr {
            val list = ArrayList<JsonValue>()
            expect('['); skipWs()
            if (peek() == ']') { i++; return Arr(list) }
            while (true) {
                list += parseValue()
                skipWs()
                when (peek()) {
                    ',' -> { i++; continue }
                    ']' -> { i++; break }
                    else -> throw IllegalArgumentException("Expected ',' or ']' at $i")
                }
            }
            return Arr(list)
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (i < s.length) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        val e = s[i++]
                        when (e) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                            'b' -> sb.append('\b'); 'f' -> sb.append('')
                            'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
            throw IllegalArgumentException("Unterminated string")
        }

        private fun parseNumber(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDouble()
        }

        private fun parseBool(): Bool =
            if (s.startsWith("true", i)) { i += 4; Bool(true) }
            else if (s.startsWith("false", i)) { i += 5; Bool(false) }
            else throw IllegalArgumentException("Invalid literal at $i")

        private fun parseNull(): JsonValue =
            if (s.startsWith("null", i)) { i += 4; Null }
            else throw IllegalArgumentException("Invalid literal at $i")

        private fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }
        private fun peek(): Char = if (i < s.length) s[i] else ' '
        private fun expect(c: Char) {
            if (peek() != c) throw IllegalArgumentException("Expected '$c' at $i")
            i++
        }
    }
}
