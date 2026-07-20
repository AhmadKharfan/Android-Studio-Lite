package com.ahmadkharfan.androidstudiolite.core.xml

/** Lightweight dependency-free XML model produced by [XmlParser]. */
enum class XmlNodeType {
    DOCUMENT, ELEMENT, ATTRIBUTE, TEXT, COMMENT, CDATA, PROCESSING, DOCTYPE, MALFORMED,
}

class XmlNode(
    val type: XmlNodeType,
    val name: String?,
    start: Int,
    end: Int,
    private val source: CharSequence,
) {
    var start: Int = start
        internal set
    var end: Int = end
        internal set
    var selfClosing: Boolean = false
        internal set
    var valueStart: Int = -1
        internal set
    var valueEnd: Int = -1
        internal set

    var parent: XmlNode? = null
        private set
    private val nodes = ArrayList<XmlNode>()
    val children: List<XmlNode> get() = nodes

    internal fun append(child: XmlNode) {
        child.parent = this
        nodes += child
    }

    val attributes: List<XmlNode> get() = nodes.filter { it.type == XmlNodeType.ATTRIBUTE }
    fun hasValue(): Boolean = valueStart >= 0

    fun valueText(): String? {
        if (valueStart < 0) return null
        val start = valueStart.coerceIn(0, source.length)
        return source.subSequence(start, valueEnd.coerceIn(start, source.length)).toString()
    }
}

data class XmlIssue(val start: Int, val end: Int, val code: String, val message: String)

object XmlIssueCodes {
    const val STRAY_CLOSE = "xml.strayClose"
    const val EXPECTED_NAME = "xml.expectedName"
    const val MALFORMED_TAG = "xml.malformedTag"
    const val UNCLOSED_TAG = "xml.unclosedTag"
    const val UNTERMINATED_VALUE = "xml.unterminatedValue"
    const val UNQUOTED_VALUE = "xml.unquotedValue"
}

class ParsedXml(val root: XmlNode, val issues: List<XmlIssue>) {
    fun elements(): List<XmlNode> {
        val result = ArrayList<XmlNode>()
        fun visit(node: XmlNode) {
            if (node.type == XmlNodeType.ELEMENT) result += node
            node.children.forEach(::visit)
        }
        visit(root)
        return result
    }

    fun rootElement(): XmlNode? = elements().firstOrNull()

    fun nodeCovering(offset: Int): XmlNode {
        var current = root
        while (true) {
            val next = current.children.lastOrNull { offset in it.start..it.end } ?: break
            current = next
        }
        return current
    }
}
