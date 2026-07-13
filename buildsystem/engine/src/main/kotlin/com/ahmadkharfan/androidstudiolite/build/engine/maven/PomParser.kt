package com.ahmadkharfan.androidstudiolite.build.engine.maven

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Reads a Maven POM into a [Pom]. Uses the JDK/Android DOM parser (cleanroom — no Maven libraries).
 * Only the elements the resolver needs are extracted: coordinates, parent, properties,
 * dependencyManagement, and dependencies with scope/optional/exclusions. Unknown elements are
 * ignored, matching how Maven treats a superset schema.
 */
object PomParser {

    private val factory: DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            // POMs are trusted local files, but disable external entity resolution defensively.
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            isNamespaceAware = false
            isValidating = false
        }

    fun parse(bytes: ByteArray): Pom {
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        val project = doc.documentElement ?: error("Empty POM")

        val parentEl = project.child("parent")
        val parent = parentEl?.let {
            MavenCoordinate(
                group = it.childText("groupId").orEmpty(),
                artifact = it.childText("artifactId").orEmpty(),
                version = it.childText("version"),
                extension = "pom",
            )
        }

        val properties = project.child("properties")?.let { props ->
            props.childElements().associate { it.tagName to it.textValue() }
        } ?: emptyMap()

        val managed = project.child("dependencyManagement")
            ?.child("dependencies")
            ?.let { dependencies(it) }
            ?: emptyList()

        val deps = project.child("dependencies")?.let { dependencies(it) } ?: emptyList()

        return Pom(
            group = project.childText("groupId") ?: parent?.group,
            artifact = project.childText("artifactId").orEmpty(),
            version = project.childText("version") ?: parent?.version,
            packaging = project.childText("packaging") ?: "jar",
            parent = parent,
            properties = properties,
            managedDependencies = managed,
            dependencies = deps,
        )
    }

    private fun dependencies(dependenciesEl: Element): List<PomDependency> =
        dependenciesEl.childElements("dependency").map { dep ->
            PomDependency(
                group = dep.childText("groupId").orEmpty(),
                artifact = dep.childText("artifactId").orEmpty(),
                version = dep.childText("version"),
                scope = dep.childText("scope"),
                type = dep.childText("type"),
                classifier = dep.childText("classifier"),
                optional = dep.childText("optional")?.equals("true", ignoreCase = true) ?: false,
                exclusions = dep.child("exclusions")?.childElements("exclusion")?.map {
                    (it.childText("groupId").orEmpty()) to (it.childText("artifactId").orEmpty())
                } ?: emptyList(),
            )
        }

    // ---- tiny DOM helpers -------------------------------------------------------------------

    private fun Element.childElements(tag: String? = null): List<Element> {
        val out = ArrayList<Element>()
        val kids = childNodes
        for (i in 0 until kids.length) {
            val n = kids.item(i)
            if (n.nodeType == Node.ELEMENT_NODE) {
                val e = n as Element
                if (tag == null || e.tagName == tag) out += e
            }
        }
        return out
    }

    private fun Element.child(tag: String): Element? = childElements(tag).firstOrNull()

    private fun Element.childText(tag: String): String? = child(tag)?.textValue()

    private fun Element.textValue(): String = textContent.trim()
}
