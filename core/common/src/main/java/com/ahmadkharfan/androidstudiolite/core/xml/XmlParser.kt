package com.ahmadkharfan.androidstudiolite.core.xml

/**
 * Forgiving, single-pass XML parser shared by project inspection and editor language tooling.
 * It records malformed input rather than throwing so callers can process in-progress documents.
 */
class XmlParser(private val src: CharSequence) {
    private val length = src.length
    private var cursor = 0
    private val issues = ArrayList<XmlIssue>()

    fun parse(): ParsedXml {
        val document = XmlNode(XmlNodeType.DOCUMENT, null, 0, length, src)
        val open = ArrayDeque<XmlNode>().apply { addLast(document) }
        while (cursor < length) {
            if (src[cursor] != '<') {
                open.last().append(readText())
                continue
            }
            when {
                lookingAt("<!--") -> open.last().append(readDelimited(XmlNodeType.COMMENT, "-->"))
                lookingAt("<![CDATA[") -> open.last().append(readDelimited(XmlNodeType.CDATA, "]]>"))
                lookingAt("<?") -> open.last().append(readDelimited(XmlNodeType.PROCESSING, "?>"))
                lookingAt("<!") -> open.last().append(readDoctype())
                lookingAt("</") -> handleCloseTag(open)
                cursor + 1 < length && isNameStart(src[cursor + 1]) -> handleOpenTag(open)
                else -> {
                    open.last().append(XmlNode(XmlNodeType.MALFORMED, null, cursor, cursor + 1, src))
                    cursor++
                }
            }
        }
        while (open.size > 1) reportUnclosed(open.removeLast(), length)
        document.end = length
        return ParsedXml(document, issues)
    }

    private enum class TagClose { OPEN, SELF_CLOSED, TRUNCATED }

    private fun handleOpenTag(open: ArrayDeque<XmlNode>) {
        val start = cursor++
        val name = readName()
        val element = XmlNode(XmlNodeType.ELEMENT, name, start, cursor, src)
        if (name.isEmpty()) report(start, cursor, XmlIssueCodes.EXPECTED_NAME, "Expected element name")
        when (readAttributesInto(element)) {
            TagClose.SELF_CLOSED -> {
                element.selfClosing = true
                element.end = cursor
                open.last().append(element)
            }
            TagClose.OPEN -> {
                element.end = cursor
                open.last().append(element)
                open.addLast(element)
            }
            TagClose.TRUNCATED -> {
                report(start, cursor, XmlIssueCodes.MALFORMED_TAG, "Malformed start tag for <$name>")
                element.end = cursor
                open.last().append(element)
            }
        }
    }

    private fun handleCloseTag(open: ArrayDeque<XmlNode>) {
        val start = cursor
        cursor += 2
        val name = readName()
        while (cursor < length && src[cursor] != '>' && src[cursor] != '<') cursor++
        if (cursor < length && src[cursor] == '>') cursor++
        val end = cursor
        val match = open.indices.reversed().firstOrNull {
            open[it].type == XmlNodeType.ELEMENT && open[it].name == name && name.isNotEmpty()
        } ?: -1
        if (match < 0) {
            report(start, end, XmlIssueCodes.STRAY_CLOSE, "Unexpected closing tag")
            open.last().append(XmlNode(XmlNodeType.MALFORMED, name, start, end, src))
            return
        }
        while (open.size - 1 > match) reportUnclosed(open.removeLast(), start)
        open.removeLast().end = end
    }

    private fun readAttributesInto(element: XmlNode): TagClose {
        while (cursor < length) {
            skipWhitespace()
            if (cursor >= length) return TagClose.TRUNCATED
            when (val c = src[cursor]) {
                '>' -> { cursor++; return TagClose.OPEN }
                '/' -> if (cursor + 1 < length && src[cursor + 1] == '>') {
                    cursor += 2
                    return TagClose.SELF_CLOSED
                } else cursor++
                '<' -> return TagClose.TRUNCATED
                else -> if (isNameStart(c)) element.append(readAttribute()) else cursor++
            }
        }
        return TagClose.TRUNCATED
    }

    private fun readAttribute(): XmlNode {
        val start = cursor
        val name = readName()
        val attribute = XmlNode(XmlNodeType.ATTRIBUTE, name, start, cursor, src)
        skipWhitespace()
        if (src.getOrNull(cursor) == '=') {
            cursor++
            skipWhitespace()
            val opener = src.getOrNull(cursor)
            if (opener == '"' || opener == '\'') {
                cursor++
                val valueStart = cursor
                while (cursor < length && src[cursor] != opener && src[cursor] != '<') cursor++
                attribute.valueStart = valueStart
                attribute.valueEnd = cursor
                if (src.getOrNull(cursor) == opener) cursor++
                else report(valueStart, cursor, XmlIssueCodes.UNTERMINATED_VALUE, "Unterminated attribute value")
            } else {
                val valueStart = cursor
                while (cursor < length && !src[cursor].isWhitespace() && src[cursor] != '>' && src[cursor] != '<') cursor++
                attribute.valueStart = valueStart
                attribute.valueEnd = cursor
                if (cursor > valueStart) {
                    report(valueStart, cursor, XmlIssueCodes.UNQUOTED_VALUE, "Attribute value must be quoted")
                }
            }
        }
        attribute.end = cursor
        return attribute
    }

    private fun readText(): XmlNode {
        val start = cursor
        while (cursor < length && src[cursor] != '<') cursor++
        return XmlNode(XmlNodeType.TEXT, null, start, cursor, src)
    }

    private fun readDelimited(type: XmlNodeType, closing: String): XmlNode {
        val start = cursor
        val hit = indexOf(closing, cursor)
        cursor = if (hit < 0) length else hit + closing.length
        return XmlNode(type, null, start, cursor, src)
    }

    private fun readDoctype(): XmlNode {
        val start = cursor
        while (cursor < length && src[cursor] != '>') cursor++
        if (cursor < length) cursor++
        return XmlNode(XmlNodeType.DOCTYPE, null, start, cursor, src)
    }

    private fun readName(): String {
        val start = cursor
        if (cursor < length && isNameStart(src[cursor])) {
            cursor++
            while (cursor < length && isNameChar(src[cursor])) cursor++
        }
        return src.subSequence(start, cursor).toString()
    }

    private fun skipWhitespace() {
        while (cursor < length && src[cursor].isWhitespace()) cursor++
    }

    private fun lookingAt(literal: String): Boolean =
        cursor + literal.length <= length && literal.indices.all { src[cursor + it] == literal[it] }

    private fun indexOf(needle: String, from: Int): Int {
        for (index in from..(length - needle.length)) {
            if (needle.indices.all { src[index + it] == needle[it] }) return index
        }
        return -1
    }

    private fun reportUnclosed(element: XmlNode, at: Int) {
        element.end = at
        report(element.start, at, XmlIssueCodes.UNCLOSED_TAG, "Missing closing tag </${element.name}>")
    }

    private fun report(start: Int, end: Int, code: String, message: String) {
        issues += XmlIssue(start, end, code, message)
    }

    private companion object {
        fun isNameStart(c: Char): Boolean = c.isLetter() || c == '_' || c == ':'
        fun isNameChar(c: Char): Boolean =
            c.isLetterOrDigit() || c == '_' || c == ':' || c == '.' || c == '-'
    }
}
