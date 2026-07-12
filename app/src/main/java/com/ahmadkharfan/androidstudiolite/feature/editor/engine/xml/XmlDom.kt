package com.ahmadkharfan.androidstudiolite.feature.editor.engine.xml


object XmlNodeKinds {
    const val DOCUMENT = "compilation_unit"
    const val TAG = "xml_tag"
    const val ATTRIBUTE = "xml_attribute"
    const val ATTR_VALUE = "xml_attr_value"
    const val TEXT = "xml_text"
    const val COMMENT = "xml_comment"
    const val CDATA = "xml_cdata"
    const val PROLOG = "xml_prolog"
    const val DOCTYPE = "xml_doctype"
    const val ERROR = "error"
}

data class XmlRange(val start: Int, val end: Int) {
    operator fun contains(offset: Int): Boolean = offset in start until end || (start == end && offset == start)
    fun intersects(other: XmlRange): Boolean = start < other.end && other.start < end || this == other
}

class XmlNode(
    val kind: String,
    start: Int,
    end: Int,
    private val source: CharSequence,
    val name: String? = null,
) {
    var startOffset: Int = start
        internal set
    var endOffset: Int = end
        internal set
    var selfClosed: Boolean = false
        internal set
    val range: XmlRange get() = XmlRange(startOffset, endOffset)
    var parent: XmlNode? = null
        internal set
    private val kids = ArrayList<XmlNode>()
    val children: List<XmlNode> get() = kids

    internal fun add(child: XmlNode) {
        child.parent = this
        kids.add(child)
    }

    internal fun close(end: Int) { endOffset = end }

    fun text(): CharSequence {
        val s = startOffset.coerceIn(0, source.length)
        val e = endOffset.coerceIn(s, source.length)
        return source.subSequence(s, e)
    }

    val attributes: List<XmlNode> get() = kids.filter { it.kind == XmlNodeKinds.ATTRIBUTE }
    val childTags: List<XmlNode> get() = kids.filter { it.kind == XmlNodeKinds.TAG }
    val valueNode: XmlNode? get() = kids.firstOrNull { it.kind == XmlNodeKinds.ATTR_VALUE }
}

class XmlParsedFile(
    val root: XmlNode,
    val diagnostics: List<Diagnosticish>,
) {
    fun text(): CharSequence = root.text()

    fun nodeAt(offset: Int): XmlNode {
        var best: XmlNode = root
        fun descend(node: XmlNode) {
            for (child in node.children) {
                if (offset in child.range) {
                    best = child
                    descend(child)
                }
            }
        }
        descend(root)
        return best
    }
}

data class Diagnosticish(val start: Int, val end: Int, val message: String, val code: String)
