package com.ahmadkharfan.androidstudiolite.build.engine.maven

import java.io.File

/** A dependency the resolver was asked to resolve, plus whether it is a BOM/platform import. */
data class ResolutionRequest(
    val coordinate: MavenCoordinate,
    /** Force BOM treatment even before its POM is read (from a `platform(...)` declaration). */
    val isPlatform: Boolean = false,
)

/** A concrete artifact chosen by resolution and materialised in the cache. */
data class ResolvedArtifact(
    val coordinate: MavenCoordinate,
    val file: File,
    /** POM packaging: "jar", "aar", "bundle", … */
    val packaging: String,
)

/** Everything a resolution produced: chosen artifacts + human-readable notes about what it skipped. */
data class ResolutionResult(
    val artifacts: List<ResolvedArtifact>,
    val warnings: List<String>,
)

/**
 * On-device transitive Maven resolver: reads POMs (with parent inheritance, property interpolation and
 * BOM/`<dependencyManagement>` import), walks the compile+runtime graph, and applies **newest-wins**
 * conflict resolution (Gradle's default), honouring `<optional>` and `<exclusions>`. Artifacts are
 * fetched through [MavenRepositories] so results are cached for offline reuse.
 *
 * Deliberately scoped to the declarative subset real Android app dependencies use; version ranges are
 * reduced to a single concrete version and any coordinate it cannot version is reported as a warning
 * rather than failing the whole resolution.
 */
class MavenResolver(private val repositories: MavenRepositories) {

    private val pomCache = HashMap<String, Pom?>()

    fun resolve(requests: List<ResolutionRequest>): ResolutionResult {
        val warnings = ArrayList<String>()
        val selected = LinkedHashMap<String, Selection>() // moduleId -> selection
        val globalManaged = HashMap<String, String>()     // moduleId -> version, from platform BOMs

        // Seed dependency management from platform/BOM roots first so versionless deps can resolve.
        val artifactRoots = ArrayList<Pair<MavenCoordinate, List<Pair<String, String>>>>()
        for (req in requests) {
            val effective = loadEffectivePom(req.coordinate)
            if (req.isPlatform || (effective?.isBom == true)) {
                effective?.let { importManaged(it, globalManaged, warnings) }
                    ?: warnings.add("Could not read BOM ${req.coordinate}")
            } else {
                artifactRoots += req.coordinate to emptyList()
            }
        }

        val work = ArrayDeque<Node>()
        artifactRoots.forEach { (coord, ex) -> work.add(Node(coord, ex)) }

        while (work.isNotEmpty()) {
            val node = work.removeFirst()
            val moduleId = node.coordinate.moduleId

            val version = node.coordinate.version
                ?: globalManaged[moduleId]
                ?: run { warnings.add("No version for ${node.coordinate} (no BOM provides it) — skipped"); null }
            if (version == null) continue
            val coord = node.coordinate.withVersion(version)

            val existing = selected[moduleId]
            if (existing != null && !MavenVersion.isNewer(version, existing.version)) continue

            val pom = loadEffectivePom(coord)
            if (pom == null) {
                warnings.add("Could not read POM for $coord — skipped")
                continue
            }
            if (pom.isBom) {
                importManaged(pom, globalManaged, warnings)
                continue
            }

            val file = repositories.getFile(coord.copy(extension = pom.artifactExtension()).relativePath())
            if (file == null) {
                warnings.add("Artifact not found for $coord (.${pom.artifactExtension()}) — skipped")
                continue
            }
            selected[moduleId] = Selection(version, ResolvedArtifact(coord, file, pom.packaging))

            val managed = managedVersions(pom, globalManaged)
            for (dep in pom.dependencies) {
                if (dep.optional) continue
                if (dep.scope != null && dep.scope !in COMPILE_RUNTIME) continue
                if (isExcluded(dep, node.exclusions)) continue
                val depVersion = dep.version?.let { interpolate(it, pom) }?.let(::normalizeVersion)
                    ?: managed[dep.moduleId]
                    ?: globalManaged[dep.moduleId]
                val childCoord = MavenCoordinate(dep.group, dep.artifact, depVersion, dep.classifier)
                work.add(Node(childCoord, node.exclusions + dep.exclusions))
            }
        }

        return ResolutionResult(selected.values.map { it.artifact }, warnings)
    }

    // ---- effective POM (parent inheritance + property interpolation) -------------------------

    private fun loadEffectivePom(coordinate: MavenCoordinate): Pom? {
        val version = coordinate.version ?: return null
        val key = "${coordinate.moduleId}:$version"
        pomCache[key]?.let { return it }
        if (pomCache.containsKey(key)) return null

        val bytes = repositories.get(coordinate.copy(extension = "pom").relativePath())
        val pom = bytes?.let { runCatching { PomParser.parse(it) }.getOrNull() }
            ?.let { mergeParent(it) }
        pomCache[key] = pom
        return pom
    }

    /** Merge the parent chain: inherit properties and dependencyManagement (child overrides parent). */
    private fun mergeParent(pom: Pom): Pom {
        val parentCoord = pom.parent ?: return pom
        val parent = loadEffectivePom(parentCoord.copy(extension = "pom")) ?: return pom
        val mergedProps = LinkedHashMap(parent.properties).apply { putAll(pom.properties) }
        val mergedManaged = LinkedHashMap<String, PomDependency>()
        parent.managedDependencies.forEach { mergedManaged[it.moduleId] = it }
        pom.managedDependencies.forEach { mergedManaged[it.moduleId] = it }
        return pom.copy(
            group = pom.group ?: parent.group,
            version = pom.version ?: parent.version,
            properties = mergedProps,
            managedDependencies = mergedManaged.values.toList(),
        )
    }

    /** Resolve versions declared via this POM's dependencyManagement, expanding imported BOMs. */
    private fun managedVersions(pom: Pom, globalManaged: Map<String, String>): Map<String, String> {
        val out = HashMap<String, String>()
        for (m in pom.managedDependencies) {
            val version = m.version?.let { interpolate(it, pom) }?.let(::normalizeVersion) ?: continue
            if (m.scope == "import" && (m.type == "pom")) {
                val bom = loadEffectivePom(MavenCoordinate(m.group, m.artifact, version, extension = "pom"))
                bom?.let { out.putAll(managedVersions(it, globalManaged)) }
            } else {
                out[m.moduleId] = version
            }
        }
        return out
    }

    private fun importManaged(pom: Pom, into: MutableMap<String, String>, warnings: MutableList<String>) {
        val versions = managedVersions(pom, into)
        if (versions.isEmpty()) warnings.add("BOM ${pom.group}:${pom.artifact} declared no managed versions")
        // Existing entries win so an earlier-declared BOM/platform pins the version.
        for ((k, v) in versions) into.putIfAbsent(k, v)
    }

    // ---- helpers -----------------------------------------------------------------------------

    private fun isExcluded(dep: PomDependency, exclusions: List<Pair<String, String>>): Boolean =
        exclusions.any { (g, a) ->
            (g == "*" || g == dep.group) && (a == "*" || a == dep.artifact)
        }

    private fun interpolate(value: String, pom: Pom): String {
        if (!value.contains("\${")) return value
        val builtins = mapOf(
            "project.version" to (pom.version ?: ""),
            "project.groupId" to (pom.group ?: ""),
            "project.artifactId" to pom.artifact,
            "pom.version" to (pom.version ?: ""),
        )
        var result = value
        var guard = 0
        while (result.contains("\${") && guard++ < 10) {
            result = Regex("\\$\\{([^}]+)}").replace(result) { m ->
                val name = m.groupValues[1]
                pom.properties[name] ?: builtins[name] ?: m.value
            }
        }
        return result
    }

    /** Reduce a version range like `[1.2,2.0)` or `[1.2]` to a single concrete version. */
    private fun normalizeVersion(version: String): String {
        val v = version.trim()
        if (v.isEmpty() || (!v.startsWith("[") && !v.startsWith("("))) return v
        val inner = v.trim('[', ']', '(', ')')
        val first = inner.split(',').firstOrNull { it.isNotBlank() }?.trim()
        return first ?: v
    }

    private fun Pom.artifactExtension(): String = when (packaging) {
        "aar" -> "aar"
        "pom" -> "pom"
        else -> "jar" // jar, bundle, maven-plugin, etc. all ship a .jar
    }

    private data class Node(val coordinate: MavenCoordinate, val exclusions: List<Pair<String, String>>)

    private data class Selection(val version: String, val artifact: ResolvedArtifact)

    private companion object {
        // Scopes contributing to a compile+runtime classpath. `provided`/`system`/`test` are excluded.
        val COMPILE_RUNTIME = setOf("compile", "runtime")
    }
}
