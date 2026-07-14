package com.ahmadkharfan.androidstudiolite.build.engine.maven

/**
 * A Maven artifact coordinate. [version] is null for a coordinate whose version is expected to come
 * from dependency management (a BOM). [extension] is the packaging/type used to locate the file
 * ("jar", "aar", "pom").
 */
data class MavenCoordinate(
    val group: String,
    val artifact: String,
    val version: String? = null,
    val classifier: String? = null,
    val extension: String = "jar",
) {
    /** group:artifact — the conflict-resolution key (a module, independent of version). */
    val moduleId: String get() = "$group:$artifact"

    /** Canonical `group:artifact:version` string (or `group:artifact` when versionless). */
    override fun toString(): String =
        if (version == null) moduleId else "$group:$artifact:$version"

    /** Repository-relative path to the artifact, e.g. `g/roup/artifact/1.0/artifact-1.0.jar`. */
    fun relativePath(ext: String = extension): String {
        val v = version ?: error("Cannot build a path for a versionless coordinate: $this")
        val dir = "${group.replace('.', '/')}/$artifact/$v"
        val cls = classifier?.let { "-$it" } ?: ""
        return "$dir/$artifact-$v$cls.$ext"
    }

    fun withVersion(newVersion: String): MavenCoordinate = copy(version = newVersion)

    companion object {
        /** Parse `group:artifact[:version[:classifier]]`; extension defaults to "jar". */
        fun parse(notation: String): MavenCoordinate {
            val parts = notation.split(':')
            require(parts.size >= 2) { "Not a Maven coordinate: '$notation'" }
            return MavenCoordinate(
                group = parts[0],
                artifact = parts[1],
                version = parts.getOrNull(2)?.takeIf { it.isNotBlank() },
                classifier = parts.getOrNull(3)?.takeIf { it.isNotBlank() },
            )
        }
    }
}
