package com.ahmadkharfan.androidstudiolite.build.engine.maven

/** A dependency declaration inside a POM, before version/property resolution. */
data class PomDependency(
    val group: String,
    val artifact: String,
    val version: String?,
    val scope: String?,
    val type: String?,
    val classifier: String?,
    val optional: Boolean,
    /** `groupId:artifactId` pairs to prune from this dependency's transitive graph ('*' allowed). */
    val exclusions: List<Pair<String, String>>,
) {
    val moduleId: String get() = "$group:$artifact"
}

/** A parsed POM, pre-inheritance. Parent merging and property interpolation happen in the resolver. */
data class Pom(
    val group: String?,
    val artifact: String,
    val version: String?,
    val packaging: String,
    val parent: MavenCoordinate?,
    val properties: Map<String, String>,
    val managedDependencies: List<PomDependency>,
    val dependencies: List<PomDependency>,
) {
    /** True for a BOM: contributes only dependency management, never a runnable artifact. */
    val isBom: Boolean get() = packaging == "pom"
}
