package com.ahmadkharfan.androidstudiolite.feature.editor.engine.xml

/**
 * A forgiving, single-pass XML parser for the editor.
 *
 * It scans the source left to right, building a tree with an explicit element stack. Unlike a
 * strict parser it never throws: malformed input is recorded as [XmlIssue]s and parsing continues,
 * which is what a live code editor needs. Close tags are matched against the nearest enclosing open
 * element of the same name; any elements skipped over on the stack are reported as unclosed, and a
 * close tag with no open counterpart is reported as stray.
 *
 * Written from scratch for Android Studio Lite (no third-party code).
 */
class XmlParser(private val src: CharSequence) {

    private val length = src.length
    private var cursor = 0
    private val issues = ArrayList<XmlIssue>()

    fun parse(): ParsedXml {
        val document = XmlNode(XmlNodeType.DOCUMENT, null, 0, length, src)
        val open = ArrayDeque<XmlNode>()
        open.addLast(document)

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
                    // A '<' that begins nothing recognizable; swallow it as a stray character.
                    open.last().append(XmlNode(XmlNodeType.MALFORMED, null, cursor, cursor + 1, src))
                    cursor++
                }
            }
        }

        // Everything still on the stack reached end-of-input without a closing tag.
        while (open.size > 1) {
            val orphan = open.removeLast()
            reportUnclosed(orphan, length)
        }
        document.end = length
        return ParsedXml(document, issues)
    }

    // region tags

    private enum class TagClose { OPEN, SELF_CLOSED, TRUNCATED }

    private fun handleOpenTag(open: ArrayDeque<XmlNode>) {
        val start = cursor
        cursor++ // past '<'
        val name = readName()
        val element = XmlNode(XmlNodeType.ELEMENT, name, start, cursor, src)
        if (name.isEmpty()) {
            report(start, cursor, XmlIssueCodes.EXPECTED_NAME, "Expected element name")
        }

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
        cursor += 2 // past '</'
        val name = readName()
        while (cursor < length && src[cursor] != '>' && src[cursor] != '<') cursor++
        if (cursor < length && src[cursor] == '>') cursor++
        val end = cursor

        // Locate the nearest enclosing element on the stack that this tag can close.
        var match = -1
        for (i in open.indices.reversed()) {
            val node = open[i]
            if (node.type == XmlNodeType.ELEMENT && node.name == name && name.isNotEmpty()) {
                match = i
                break
            }
        }

        if (match < 0) {
            report(start, end, XmlIssueCodes.STRAY_CLOSE, "Unexpected closing tag")
            open.last().append(XmlNode(XmlNodeType.MALFORMED, name, start, end, src))
            return
        }

        // Pop and report every element between the top of the stack and the matched one.
        while (open.size - 1 > match) {
            val orphan = open.removeLast()
            reportUnclosed(orphan, start)
        }
        val matched = open.removeLast()
        matched.end = end
    }

    private fun readAttributesInto(element: XmlNode): TagClose {
        while (cursor < length) {
            skipWhitespace()
            if (cursor >= length) return TagClose.TRUNCATED
            val c = src[cursor]
            when {
                c == '>' -> { cursor++; return TagClose.OPEN }
                c == '/' && cursor + 1 < length && src[cursor + 1] == '>' -> { cursor += 2; return TagClose.SELF_CLOSED }
                c == '<' -> return TagClose.TRUNCATED
                isNameStart(c) -> element.append(readAttribute())
                else -> cursor++
            }
        }
        return TagClose.TRUNCATED
    }

    private fun readAttribute(): XmlNode {
        val start = cursor
        val name = readName()
        val attribute = XmlNode(XmlNodeType.ATTRIBUTE, name, start, cursor, src)
        skipWhitespace()
        if (cursor < length && src[cursor] == '=') {
            cursor++
            skipWhitespace()
            val opener = src.getOrNull(cursor)
            if (opener == '"' || opener == '\'') {
                cursor++ // past opening quote
                val valueStart = cursor
                while (cursor < length && src[cursor] != opener && src[cursor] != '<') cursor++
                attribute.valueStart = valueStart
                attribute.valueEnd = cursor
                if (cursor < length && src[cursor] == opener) {
                    cursor++
                } else {
                    report(valueStart, cursor, XmlIssueCodes.UNTERMINATED_VALUE, "Unterminated attribute value")
                }
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

    // endregion

    // region primitives

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

    private fun lookingAt(literal: String): Boolean {
        if (cursor + literal.length > length) return false
        for (k in literal.indices) if (src[cursor + k] != literal[k]) return false
        return true
    }

    private fun indexOf(needle: String, from: Int): Int {
        var i = from
        while (i + needle.length <= length) {
            var k = 0
            while (k < needle.length && src[i + k] == needle[k]) k++
            if (k == needle.length) return i
            i++
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

    // endregion

    private companion object {
        fun isNameStart(c: Char): Boolean = c.isLetter() || c == '_' || c == ':'
        fun isNameChar(c: Char): Boolean =
            c.isLetterOrDigit() || c == '_' || c == ':' || c == '.' || c == '-'
    }
}
