package com.ahmadkharfan.androidstudiolite.feature.editor.engine.xml

/**
 * Lightweight XML document model produced by [XmlParser].
 *
 * This is an original, dependency-free representation written for Android Studio Lite. It keeps
 * only what the editor backend needs: node kinds, source offsets, a parent/child tree for locating
 * the element under a caret, and — for attributes — the span of the quoted value.
 */

/** The kinds of markup an [XmlNode] can represent. */
enum class XmlNodeType {
    DOCUMENT,
    ELEMENT,
    ATTRIBUTE,
    TEXT,
    COMMENT,
    CDATA,
    PROCESSING,
    DOCTYPE,
    MALFORMED,
}

/**
 * A single node in the parse tree. Offsets are half-open `[start, end)` into the original source.
 * For [XmlNodeType.ATTRIBUTE] nodes, [valueStart]/[valueEnd] delimit the raw (unquoted) value when
 * one was present.
 */
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

    /** Attributes declared directly on this element, in source order. */
    val attributes: List<XmlNode> get() = nodes.filter { it.type == XmlNodeType.ATTRIBUTE }

    /** Whether this node carries a `=value` clause (even an empty or malformed one). */
    fun hasValue(): Boolean = valueStart >= 0

    /** The raw attribute value text, or `null` when the attribute has no value clause. */
    fun valueText(): String? {
        if (valueStart < 0) return null
        val s = valueStart.coerceIn(0, source.length)
        val e = valueEnd.coerceIn(s, source.length)
        return source.subSequence(s, e).toString()
    }
}

/** A structural (well-formedness) problem found while parsing. */
data class XmlIssue(val start: Int, val end: Int, val code: String, val message: String)

object XmlIssueCodes {
    const val STRAY_CLOSE = "xml.strayClose"
    const val EXPECTED_NAME = "xml.expectedName"
    const val MALFORMED_TAG = "xml.malformedTag"
    const val UNCLOSED_TAG = "xml.unclosedTag"
    const val UNTERMINATED_VALUE = "xml.unterminatedValue"
    const val UNQUOTED_VALUE = "xml.unquotedValue"
}

/** The result of parsing: the document root plus every well-formedness issue encountered. */
class ParsedXml(val root: XmlNode, val issues: List<XmlIssue>) {

    /** Every [XmlNodeType.ELEMENT] node in document order. */
    fun elements(): List<XmlNode> {
        val out = ArrayList<XmlNode>()
        fun visit(node: XmlNode) {
            if (node.type == XmlNodeType.ELEMENT) out += node
            node.children.forEach(::visit)
        }
        visit(root)
        return out
    }

    /** The outermost element, or `null` for an empty/element-less document. */
    fun rootElement(): XmlNode? = elements().firstOrNull()

    /** The deepest node whose span covers [offset] (boundaries inclusive), defaulting to the root. */
    fun nodeCovering(offset: Int): XmlNode {
        var current = root
        while (true) {
            // Prefer the last matching child so that, on a shared boundary, the later sibling wins.
            val next = current.children.lastOrNull { offset in it.start..it.end } ?: break
            current = next
        }
        return current
    }
}
