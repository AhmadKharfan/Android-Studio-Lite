package com.ahmadkharfan.androidstudiolite.data.gradle.util

object MiniJson {

    fun parse(text: String): Any? = Parser(text).parseValue()

    private class Parser(private val s: String) {
        private var i = 0

        fun parseValue(): Any? {
            skipWs()
            if (i >= s.length) throw IllegalArgumentException("Unexpected end of JSON")
            return when (s[i]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                else -> parseNumber()
            }
        }

        private fun parseObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            expect('{')
            skipWs()
            if (peek() == '}') { i++; return map }
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
            return map
        }

        private fun parseArray(): List<Any?> {
            val list = ArrayList<Any?>()
            expect('[')
            skipWs()
            if (peek() == ']') { i++; return list }
            while (true) {
                list += parseValue()
                skipWs()
                when (peek()) {
                    ',' -> { i++; continue }
                    ']' -> { i++; break }
                    else -> throw IllegalArgumentException("Expected ',' or ']' at $i")
                }
            }
            return list
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (i < s.length) {
                when (val c = s[i++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        when (val e = s[i++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
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

        private fun parseBoolean(): Boolean =
            if (s.startsWith("true", i)) { i += 4; true }
            else if (s.startsWith("false", i)) { i += 5; false }
            else throw IllegalArgumentException("Invalid literal at $i")

        private fun parseNull(): Any? =
            if (s.startsWith("null", i)) { i += 4; null }
            else throw IllegalArgumentException("Invalid literal at $i")

        private fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }
        private fun peek(): Char = if (i < s.length) s[i] else '\u0000'
        private fun expect(c: Char) {
            if (peek() != c) throw IllegalArgumentException("Expected '$c' at $i")
            i++
        }
    }
}
