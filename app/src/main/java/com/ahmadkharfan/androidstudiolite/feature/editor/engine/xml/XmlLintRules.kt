package com.ahmadkharfan.androidstudiolite.feature.editor.engine.xml

object XmlLintRules {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private val TEXT_ATTRS = setOf("android:text", "android:hint", "android:contentDescription")
    private val SIZELESS_TAGS = setOf("merge", "include", "ViewStub", "requestFocus", "tag")

    private val KNOWN_NAMESPACES = linkedMapOf(
        "android" to ANDROID_NS,
        "app" to "http://schemas.android.com/apk/res-auto",
        "tools" to "http://schemas.android.com/tools",
    )

    data class MissingNamespace(val prefix: String, val uri: String, val range: XmlRange, val insertAt: Int)
    data class HardcodedText(val range: XmlRange, val attrName: String, val value: String)
    data class MissingSize(val range: XmlRange, val tag: String, val dim: String, val insertAt: Int)

    fun allTags(parsed: XmlParsedFile): List<XmlNode> {
        val out = ArrayList<XmlNode>()
        fun walk(n: XmlNode) {
            if (n.kind == XmlNodeKinds.TAG) out += n
            n.children.forEach(::walk)
        }
        walk(parsed.root)
        return out
    }

    fun missingNamespaces(parsed: XmlParsedFile): List<MissingNamespace> {
        val tags = allTags(parsed)
        val root = tags.firstOrNull() ?: return emptyList()
        val declared = root.attributes.mapNotNull { it.name }
            .filter { it.startsWith("xmlns:") }.mapTo(HashSet()) { it.removePrefix("xmlns:") }
        val at = root.startOffset + 1 + (root.name?.length ?: 0)
        val range = XmlRange(root.startOffset, at)
        return KNOWN_NAMESPACES.mapNotNull { (prefix, uri) ->
            if (prefix in declared) return@mapNotNull null
            val used = tags.any { t -> t.attributes.any { it.name?.startsWith("$prefix:") == true } }
            if (used) MissingNamespace(prefix, uri, range, at) else null
        }
    }

    fun hardcodedText(parsed: XmlParsedFile): List<HardcodedText> {
        val out = ArrayList<HardcodedText>()
        for (tag in allTags(parsed)) for (attr in tag.attributes) {
            val an = attr.name ?: continue
            if (an !in TEXT_ATTRS) continue
            val vnode = attr.valueNode ?: continue
            val value = vnode.text().toString()
            if (value.isBlank() || value.startsWith("@") || value.startsWith("?")) continue
            out += HardcodedText(vnode.range, an, value)
        }
        return out
    }

    fun missingSize(parsed: XmlParsedFile, isViewLike: (String) -> Boolean): List<MissingSize> {
        val out = ArrayList<MissingSize>()
        for (tag in allTags(parsed)) {
            val name = tag.name ?: continue
            if (name in SIZELESS_TAGS || !isViewLike(name)) continue
            val attrs = tag.attributes.mapNotNull { it.name }.toSet()
            val range = XmlRange(tag.startOffset + 1, tag.startOffset + 1 + name.length)
            val insertAt = tag.startOffset + 1 + name.length
            for (dim in listOf("layout_width", "layout_height")) {
                if ("android:$dim" !in attrs) out += MissingSize(range, name, dim, insertAt)
            }
        }
        return out
    }
}
