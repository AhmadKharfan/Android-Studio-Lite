package com.ahmadkharfan.androidstudiolite.feature.editor.engine.xml

/**
 * Android-specific lint checks over a [ParsedXml] tree.
 *
 * These rules encode well-known Android facts (the standard XML namespace prefixes, which layout
 * attributes are required, which attributes carry user-facing text). Diagnostic codes, messages and
 * severities are chosen by the caller ([XmlBackend]); this object only decides *where* each problem
 * is and returns the raw findings.
 *
 * Original implementation for Android Studio Lite.
 */
object XmlLint {

    /** Prefix -> namespace URI for the prefixes we know how to check. */
    private val WELL_KNOWN_PREFIXES = linkedMapOf(
        "android" to "http://schemas.android.com/apk/res/android",
        "app" to "http://schemas.android.com/apk/res-auto",
        "tools" to "http://schemas.android.com/tools",
    )

    /** Attributes whose literal value ends up shown to the user and should be a string resource. */
    private val USER_TEXT_ATTRS = setOf("android:text", "android:hint", "android:contentDescription")

    /** Container/utility tags that do not themselves need explicit layout dimensions. */
    private val DIMENSIONLESS_TAGS = setOf("merge", "include", "ViewStub", "requestFocus", "tag")

    private val REQUIRED_DIMENSIONS = listOf("layout_width", "layout_height")

    data class UndeclaredNamespace(val prefix: String, val uri: String, val start: Int, val end: Int)
    data class HardcodedString(val start: Int, val end: Int, val attribute: String, val value: String)
    data class MissingDimension(val start: Int, val end: Int, val tag: String, val dimension: String)

    /**
     * A prefix such as `android:` is "used" somewhere but never declared with `xmlns:` on the root
     * element. Reported against the opening name of the root element.
     */
    fun undeclaredNamespaces(parsed: ParsedXml): List<UndeclaredNamespace> {
        val elements = parsed.elements()
        val root = elements.firstOrNull() ?: return emptyList()

        val declared = root.attributes
            .mapNotNull { it.name }
            .filter { it.startsWith("xmlns:") }
            .mapTo(HashSet()) { it.substring("xmlns:".length) }

        val anchorStart = root.start
        val anchorEnd = root.start + 1 + (root.name?.length ?: 0)

        val findings = ArrayList<UndeclaredNamespace>()
        for ((prefix, uri) in WELL_KNOWN_PREFIXES) {
            if (prefix in declared) continue
            val used = elements.any { element ->
                element.attributes.any { it.name?.startsWith("$prefix:") == true }
            }
            if (used) findings += UndeclaredNamespace(prefix, uri, anchorStart, anchorEnd)
        }
        return findings
    }

    /** Literal text placed directly in a text-bearing attribute instead of a `@string`/`?attr` ref. */
    fun hardcodedStrings(parsed: ParsedXml): List<HardcodedString> {
        val findings = ArrayList<HardcodedString>()
        for (element in parsed.elements()) {
            for (attribute in element.attributes) {
                val name = attribute.name ?: continue
                if (name !in USER_TEXT_ATTRS) continue
                if (!attribute.hasValue()) continue
                val value = attribute.valueText().orEmpty()
                if (value.isBlank() || value.startsWith("@") || value.startsWith("?")) continue
                findings += HardcodedString(attribute.valueStart, attribute.valueEnd, name, value)
            }
        }
        return findings
    }

    /** View-like elements in a layout that omit `android:layout_width` and/or `android:layout_height`. */
    fun missingDimensions(parsed: ParsedXml, isViewLike: (String) -> Boolean = ::looksLikeView): List<MissingDimension> {
        val findings = ArrayList<MissingDimension>()
        for (element in parsed.elements()) {
            val name = element.name ?: continue
            if (name in DIMENSIONLESS_TAGS || !isViewLike(name)) continue
            val present = element.attributes.mapNotNull { it.name }.toSet()
            val nameStart = element.start + 1
            val nameEnd = nameStart + name.length
            for (dimension in REQUIRED_DIMENSIONS) {
                if ("android:$dimension" !in present) {
                    findings += MissingDimension(nameStart, nameEnd, name, dimension)
                }
            }
        }
        return findings
    }

    /** A tag is treated as a view when its simple (post-dot) name is upper-camel, e.g. `TextView`. */
    fun looksLikeView(tag: String): Boolean =
        tag.substringAfterLast('.').firstOrNull()?.isUpperCase() == true
}
