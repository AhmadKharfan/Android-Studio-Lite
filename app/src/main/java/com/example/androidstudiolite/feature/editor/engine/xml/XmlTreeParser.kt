package com.example.androidstudiolite.feature.editor.engine.xml

class XmlTreeParser(private val text: CharSequence) {

    private val len = text.length
    private var pos = 0
    private val diagnostics = ArrayList<Diagnosticish>()
    private val openStack = ArrayDeque<String>()

    fun parse(): XmlParsedFile {
        val root = XmlNode(XmlNodeKinds.DOCUMENT, 0, len, text)
        parseContent(root)
        if (pos < len) {
            root.add(XmlNode(XmlNodeKinds.ERROR, pos, len, text))
            pos = len
        }
        root.close(len)
        return XmlParsedFile(root, diagnostics)
    }

    private fun parseContent(into: XmlNode) {
        while (pos < len) {
            val c = text[pos]
            if (c == '<') {
                when {
                    startsWith("</") -> {
                        val closeName = peekCloseName()
                        if (closeName != null && openStack.contains(closeName)) return
                        val start = pos
                        val end = consumeCloseTag()
                        into.add(XmlNode(XmlNodeKinds.ERROR, start, end, text))
                        report(start, end, "Unexpected closing tag", "xml.strayClose")
                    }
                    startsWith("<!--") -> into.add(consumeUntil(XmlNodeKinds.COMMENT, "-->"))
                    startsWith("<![CDATA[") -> into.add(consumeUntil(XmlNodeKinds.CDATA, "]]>"))
                    startsWith("<?") -> into.add(consumeUntil(XmlNodeKinds.PROLOG, "?>"))
                    startsWith("<!") -> into.add(consumeDoctype())
                    pos + 1 < len && isNameStart(text[pos + 1]) -> into.add(parseElement())
                    else -> {
                        into.add(XmlNode(XmlNodeKinds.ERROR, pos, pos + 1, text))
                        pos++
                    }
                }
            } else {
                into.add(parseText())
            }
        }
    }

    private fun parseText(): XmlNode {
        val start = pos
        while (pos < len && text[pos] != '<') pos++
        return XmlNode(XmlNodeKinds.TEXT, start, pos, text)
    }

    private fun parseElement(): XmlNode {
        val start = pos
        pos++
        val name = readName()
        if (name.isEmpty()) report(start, pos, "Expected element name", "xml.expectedName")
        val element = XmlNode(XmlNodeKinds.TAG, start, pos, text, name = name)

        when (parseAttributes(element)) {
            TagEnd.SELF_CLOSED -> {
                element.selfClosed = true
                element.close(pos)
                return element
            }
            TagEnd.UNCLOSED -> {
                report(start, pos, "Malformed start tag for <$name>", "xml.malformedTag")
                element.close(pos)
                return element
            }
            TagEnd.OPEN -> {}
        }

        openStack.addLast(name)
        parseContent(element)
        openStack.removeLast()

        if (pos < len && startsWith("</")) {
            val closeName = peekCloseName()
            if (closeName == name || closeName == null || closeName.isEmpty()) {
                val end = consumeCloseTag()
                element.close(end)
            } else {
                report(start, pos, "Missing closing tag </$name>", "xml.unclosedTag")
                element.close(pos)
            }
        } else {
            report(start, len, "Missing closing tag </$name>", "xml.unclosedTag")
            element.close(pos)
        }
        return element
    }

    private enum class TagEnd { OPEN, SELF_CLOSED, UNCLOSED }

    private fun parseAttributes(element: XmlNode): TagEnd {
        while (pos < len) {
            skipWhitespace()
            if (pos >= len) return TagEnd.UNCLOSED
            val c = text[pos]
            when {
                c == '>' -> { pos++; return TagEnd.OPEN }
                startsWith("/>") -> { pos += 2; return TagEnd.SELF_CLOSED }
                c == '<' -> return TagEnd.UNCLOSED
                isNameStart(c) -> element.add(parseAttribute())
                else -> pos++
            }
        }
        return TagEnd.UNCLOSED
    }

    private fun parseAttribute(): XmlNode {
        val start = pos
        val name = readName()
        val attr = XmlNode(XmlNodeKinds.ATTRIBUTE, start, pos, text, name = name)
        skipWhitespace()
        if (pos < len && text[pos] == '=') {
            pos++
            skipWhitespace()
            if (pos < len && (text[pos] == '"' || text[pos] == '\'')) {
                val quote = text[pos]
                pos++
                val valueStart = pos
                while (pos < len && text[pos] != quote && text[pos] != '<') pos++
                val valueEnd = pos
                attr.add(XmlNode(XmlNodeKinds.ATTR_VALUE, valueStart, valueEnd, text))
                if (pos < len && text[pos] == quote) pos++ else
                    report(valueStart, pos, "Unterminated attribute value", "xml.unterminatedValue")
            } else {
                val valueStart = pos
                while (pos < len && !text[pos].isWhitespace() && text[pos] != '>' && text[pos] != '<') pos++
                attr.add(XmlNode(XmlNodeKinds.ATTR_VALUE, valueStart, pos, text))
                if (pos > valueStart) report(valueStart, pos, "Attribute value must be quoted", "xml.unquotedValue")
            }
        }
        attr.close(pos)
        return attr
    }

    private fun consumeDoctype(): XmlNode {
        val start = pos
        while (pos < len && text[pos] != '>') pos++
        if (pos < len) pos++
        return XmlNode(XmlNodeKinds.DOCTYPE, start, pos, text)
    }

    private fun consumeUntil(kind: String, terminator: String): XmlNode {
        val start = pos
        val idx = indexOf(terminator, pos)
        pos = if (idx < 0) len else idx + terminator.length
        return XmlNode(kind, start, pos, text)
    }

    private fun peekCloseName(): String? {
        if (!startsWith("</")) return null
        var i = pos + 2
        while (i < len && text[i].isWhitespace()) i++
        val s = i
        while (i < len && isNameChar(text[i])) i++
        return text.subSequence(s, i).toString()
    }

    private fun consumeCloseTag(): Int {
        pos += 2
        while (pos < len && text[pos] != '>' && text[pos] != '<') pos++
        if (pos < len && text[pos] == '>') pos++
        return pos
    }

    private fun readName(): String {
        val s = pos
        if (pos < len && isNameStart(text[pos])) {
            pos++
            while (pos < len && isNameChar(text[pos])) pos++
        }
        return text.subSequence(s, pos).toString()
    }

    private fun skipWhitespace() { while (pos < len && text[pos].isWhitespace()) pos++ }

    private fun startsWith(s: String): Boolean {
        if (pos + s.length > len) return false
        for (i in s.indices) if (text[pos + i] != s[i]) return false
        return true
    }

    private fun indexOf(needle: String, from: Int): Int {
        var i = from
        outer@ while (i + needle.length <= len) {
            for (j in needle.indices) if (text[i + j] != needle[j]) { i++; continue@outer }
            return i
        }
        return -1
    }

    private fun report(start: Int, end: Int, message: String, code: String) {
        diagnostics.add(Diagnosticish(start, end, message, code))
    }

    private fun isNameStart(c: Char): Boolean = c.isLetter() || c == '_' || c == ':'
    private fun isNameChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == ':' || c == '.' || c == '-'
}
