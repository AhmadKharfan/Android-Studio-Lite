/**
 * Pure module-boundary rules, deliberately free of Gradle types so they can be unit tested directly.
 *
 * A [ModuleEdge] is one declared project-to-project dependency. The Gradle plugin collects the edges
 * and hands them here; everything about *whether an edge is allowed* lives in this file.
 */
/** An external (non-project) dependency declared by a module. */
data class ExternalEdge(
    val from: String,
    val configuration: String,
    val group: String,
    val name: String,
)

data class ModuleEdge(
    val from: String,
    val configuration: String,
    val to: String,
) {
    override fun toString(): String = "$from --($configuration)--> $to"
}

data class BoundaryViolation(
    val edge: ModuleEdge,
    val rule: String,
    val explanation: String,
) {
    fun render(): String = "  ${edge.from} -> ${edge.to}  [${edge.configuration}]\n      $rule: $explanation"
}

/**
 * Configurations that carry production code. Test-only and tooling configurations are exempt: a
 * feature may legitimately exercise a real data implementation from its own tests.
 */
fun isProductionConfiguration(name: String): Boolean {
    // Matched on the camelCase name, not lowercased: Gradle/AGP name test configurations either
    // `test...` or `...Test...` (testImplementation, androidTestApi, debugUnitTestRuntimeOnly).
    // Lowercasing first would also exempt a configuration that merely contains the letters "test",
    // such as `contestImplementation`, leaving an unguarded production configuration.
    if (name.startsWith("test") || name.contains("Test")) return false
    val lower = name.lowercase()
    val toolingPrefixes = listOf("ksp", "kapt", "detekt", "lint", "annotationprocessor", "compiler")
    return toolingPrefixes.none { lower.startsWith(it) }
}

private fun isFeature(path: String) = path.startsWith(":feature:")

/** Contract module. Its sibling `:presentation` module holds the implementation. */
private fun isApiModule(path: String) = path.endsWith(":api")
private fun isData(path: String) = path.startsWith(":data:")

/**
 * @param baseline edges that already violate a rule and are being burned down. An edge listed here
 *   is reported as an allowed exception; an edge NOT listed fails the build immediately, so the
 *   graph can only improve.
 */
fun findBoundaryViolations(
    edges: List<ModuleEdge>,
    baseline: Set<String> = emptySet(),
): List<BoundaryViolation> = edges
    .filter { isProductionConfiguration(it.configuration) }
    .mapNotNull { edge -> violationFor(edge) }
    .filterNot { "${it.edge.from} -> ${it.edge.to}" in baseline }

private fun violationFor(edge: ModuleEdge): BoundaryViolation? = when {
    isFeature(edge.from) && isApiModule(edge.from) && edge.to != ":domain" ->
        BoundaryViolation(
            edge,
            "api-module-scope",
            "A feature api module is a contract: it may only depend on :domain, so that depending on " +
                "a contract never drags in an implementation.",
        )

    isFeature(edge.from) && !isApiModule(edge.from) && isFeature(edge.to) && !isApiModule(edge.to) ->
        BoundaryViolation(
            edge,
            "no-feature-to-feature",
            "Features must not depend on each other's implementations. Depend on the sibling api " +
                "module instead, or have :app pass the collaboration in.",
        )

    isFeature(edge.from) && isData(edge.to) ->
        BoundaryViolation(
            edge,
            "no-feature-to-data",
            "Features must reach data through a :domain contract; :app binds the implementation. Use " +
                "testImplementation if only the tests need the real implementation.",
        )

    isData(edge.from) && isFeature(edge.to) ->
        BoundaryViolation(
            edge,
            "no-data-to-feature",
            "The data layer must not depend on a feature; that inverts the layering.",
        )

    else -> null
}

/** Edges that are allowed for now and expected to be removed. Nothing may be added to this list. */
val MODULE_BOUNDARY_BASELINE: Set<String> = setOf(
)

/**
 * Features must render through the design system, never Material directly.
 *
 * The design system owns the Material dependency so the app's look is defined in one module: a
 * feature reaching for `material3.Text` or `Scaffold` bypasses every token and quietly reintroduces
 * Material defaults. `:designsystem` itself is exempt — wrapping Material is its job.
 */
fun findMaterialViolations(edges: List<ExternalEdge>): List<BoundaryViolation> = edges
    .filter { isProductionConfiguration(it.configuration) }
    .filter { isFeature(it.from) }
    .filter { it.group == "androidx.compose.material3" || it.group == "androidx.compose.material" }
    .map {
        BoundaryViolation(
            ModuleEdge(it.from, it.configuration, "${it.group}:${it.name}"),
            "no-material-in-features",
            "Features must depend on :designsystem instead. If a component is missing, add it there " +
                "so every feature gets the same tokens.",
        )
    }
