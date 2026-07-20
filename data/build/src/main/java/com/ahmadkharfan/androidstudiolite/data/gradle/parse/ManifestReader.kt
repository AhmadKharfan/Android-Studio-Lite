package com.ahmadkharfan.androidstudiolite.data.gradle.parse

import com.ahmadkharfan.androidstudiolite.core.xml.XmlNode
import com.ahmadkharfan.androidstudiolite.core.xml.XmlNodeType
import com.ahmadkharfan.androidstudiolite.core.xml.XmlParser

data class ManifestInfo(
    val packageName: String? = null,
    val minSdkVersion: String? = null,
    val targetSdkVersion: String? = null,
    val hasLauncherActivity: Boolean = false,
)

object ManifestReader {

    fun read(text: CharSequence): ManifestInfo {
        val parsed = XmlParser(text).parse()
        val manifest = findTag(parsed.root, "manifest") ?: return ManifestInfo()
        val usesSdk = findTag(manifest, "uses-sdk")
        return ManifestInfo(
            packageName = attr(manifest, "package"),
            minSdkVersion = usesSdk?.let { attr(it, "android:minSdkVersion") },
            targetSdkVersion = usesSdk?.let { attr(it, "android:targetSdkVersion") },
            hasLauncherActivity = hasLauncher(manifest),
        )
    }

    private fun findTag(node: XmlNode, name: String): XmlNode? {
        if (node.type == XmlNodeType.ELEMENT && node.name == name) return node
        for (child in node.children) findTag(child, name)?.let { return it }
        return null
    }

    private fun attr(tag: XmlNode, attrName: String): String? {
        val a = tag.attributes.firstOrNull { it.name == attrName } ?: return null
        return a.valueText()
    }

    private fun hasLauncher(manifest: XmlNode): Boolean {
        var found = false
        fun walk(node: XmlNode) {
            if (node.type == XmlNodeType.ELEMENT && node.name == "category" &&
                attr(node, "android:name") == "android.intent.category.LAUNCHER"
            ) found = true
            node.children.forEach(::walk)
        }
        walk(manifest)
        return found
    }
}
