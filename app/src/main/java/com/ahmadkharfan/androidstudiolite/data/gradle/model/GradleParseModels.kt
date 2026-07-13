package com.ahmadkharfan.androidstudiolite.data.gradle.model

/**
 * Raw, syntax-level results of the tolerant static Gradle reader. These sit between the
 * per-file parsers ([com.ahmadkharfan.androidstudiolite.data.gradle.parse]) and the
 * [com.ahmadkharfan.androidstudiolite.data.gradle.GradleProjectReader] that maps them into the
 * shared `ProjectModel`.
 *
 * Everything here is deliberately lossy-but-honest: values we can read statically are captured,
 * and anything we can't understand is recorded as a [GradleDiagnostic] rather than throwing. Full
 * build fidelity is a job for the real Gradle sync (full flavor); this layer only ever reports
 * what is *statically visible*.
 */

/** Severity of a static-parse observation. */
enum class DiagnosticSeverity { INFO, WARNING, ERROR }

/**
 * A structured "couldn't understand X" (or informational) note produced while reading a Gradle
 * file. [file] is the path relative to the project root when known.
 */
data class GradleDiagnostic(
    val message: String,
    val code: String,
    val severity: DiagnosticSeverity = DiagnosticSeverity.WARNING,
    val file: String? = null,
    /** Character offset into [file] where the problem starts, when known. */
    val offset: Int? = null,
)

/** The kind of DSL a build/settings script is written in, inferred from the file extension. */
enum class GradleDsl { GROOVY, KOTLIN }

/** Result of parsing `settings.gradle(.kts)`. */
data class ParsedSettings(
    val rootProjectName: String?,
    /** Included Gradle module paths, e.g. `:app`, `:build:common`. Order preserved, de-duplicated. */
    val modulePaths: List<String>,
    /** Explicit `project(":x").projectDir = file("…")` remaps, keyed by module path. */
    val projectDirOverrides: Map<String, String>,
)

/** A plugin applied by a build script. [id] is fully-qualified where resolvable. */
data class ParsedPlugin(
    val id: String,
    val version: String? = null,
    /** True when the plugin came in via `alias(libs.plugins.x)` and was catalog-resolved. */
    val fromCatalog: Boolean = false,
)

/** How a dependency was expressed in a `dependencies {}` block. */
enum class RawDependencyKind {
    /** `implementation("g:a:v")` — a Maven coordinate string. */
    MODULE,

    /** `implementation(libs.foo)` — a version-catalog accessor. */
    CATALOG,

    /** `implementation(project(":core"))` — a project dependency. */
    PROJECT,

    /** `implementation(libs.bundles.foo)` — a catalog bundle (expanded when the catalog is known). */
    CATALOG_BUNDLE,

    /** Recognised as a dependency line but the coordinate couldn't be extracted statically. */
    UNKNOWN,
}

/** A single line inside a `dependencies {}` block, before catalog resolution. */
data class RawDependency(
    val configuration: String,
    val kind: RawDependencyKind,
    /** Maven coordinate for [RawDependencyKind.MODULE]; project path for [RawDependencyKind.PROJECT]. */
    val coordinate: String? = null,
    /** Catalog accessor for CATALOG/CATALOG_BUNDLE kinds, e.g. `libs.androidx.core.ktx`. */
    val catalogAccessor: String? = null,
    /** True when wrapped in `platform(…)` / `enforcedPlatform(…)` — a BOM import. */
    val isPlatform: Boolean = false,
)

/** Statically-readable contents of an `android {}` block. */
data class ParsedAndroidBlock(
    val namespace: String? = null,
    val compileSdk: String? = null,
    val applicationId: String? = null,
    val minSdk: String? = null,
    val targetSdk: String? = null,
    val versionCode: String? = null,
    val versionName: String? = null,
    val buildTypes: List<String> = emptyList(),
    val flavorDimensions: List<String> = emptyList(),
    val productFlavors: List<String> = emptyList(),
)

/** Result of parsing a single `build.gradle(.kts)`. */
data class ParsedBuildScript(
    val dsl: GradleDsl,
    val plugins: List<ParsedPlugin> = emptyList(),
    val android: ParsedAndroidBlock? = null,
    val dependencies: List<RawDependency> = emptyList(),
)

/** A resolved library entry from `libs.versions.toml` (or an inline coordinate). */
data class CatalogLibrary(
    val alias: String,
    val group: String?,
    val name: String?,
    /** The literal version, once resolved through `version.ref`. Null if unresolved/dynamic. */
    val version: String?,
) {
    /** Full `group:name:version` coordinate when all parts are known, else `group:name`. */
    val coordinate: String?
        get() = when {
            group == null || name == null -> null
            version.isNullOrBlank() -> "$group:$name"
            else -> "$group:$name:$version"
        }
}

/** A resolved plugin entry from the `[plugins]` table of a version catalog. */
data class CatalogPlugin(
    val alias: String,
    val id: String,
    val version: String?,
)

/** Parsed `gradle/libs.versions.toml`. */
data class VersionCatalog(
    val versions: Map<String, String> = emptyMap(),
    val libraries: List<CatalogLibrary> = emptyList(),
    val plugins: List<CatalogPlugin> = emptyList(),
    val bundles: Map<String, List<String>> = emptyMap(),
) {
    private val libraryByNormalizedAlias: Map<String, CatalogLibrary> =
        libraries.associateBy { normalizeAlias(it.alias) }
    private val pluginByNormalizedAlias: Map<String, CatalogPlugin> =
        plugins.associateBy { normalizeAlias(it.alias) }

    /** Resolve a `libs.foo.bar` accessor (the part after `libs.`) to a library, tolerating `.`/`-`/`_`. */
    fun findLibrary(accessorAfterLibs: String): CatalogLibrary? =
        libraryByNormalizedAlias[normalizeAlias(accessorAfterLibs)]

    /** Resolve a `libs.plugins.foo` accessor (the part after `libs.plugins.`) to a plugin. */
    fun findPlugin(accessorAfterPlugins: String): CatalogPlugin? =
        pluginByNormalizedAlias[normalizeAlias(accessorAfterPlugins)]

    /** Expand a `libs.bundles.foo` accessor to its library aliases. */
    fun findBundle(accessorAfterBundles: String): List<String>? =
        bundles.entries.firstOrNull { normalizeAlias(it.key) == normalizeAlias(accessorAfterBundles) }?.value

    companion object {
        /** Catalog aliases treat `.`, `-`, and `_` as equivalent separators. */
        fun normalizeAlias(alias: String): String = alias.replace('.', '-').replace('_', '-')
    }
}
