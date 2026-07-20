package com.ahmadkharfan.androidstudiolite.data.templates

import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec

/**
 * A version-catalog library entry. [versionKey] names the `[versions]` entry (shared across libraries
 * that pin together); [version] is its literal value. Both are null for BOM-governed libraries (e.g.
 * Compose artifacts), which are emitted without a version. [alias] is the `[libraries]` key — the
 * generated accessor is `libs.` + alias with `-` turned into `.` (`androidx-core-ktx` → `libs.androidx.core.ktx`).
 */
data class LibrarySpec(
    val alias: String,
    val group: String,
    val name: String,
    val versionKey: String? = null,
    val version: String? = null,
) {
    /** `libs.androidx.core.ktx` accessor this library is referenced by in a build script. */
    val accessor: String get() = "libs." + alias.replace('-', '.')
}

/** A version-catalog `[plugins]` entry, referenced from a `plugins {}` block via `alias(...)`. */
data class PluginSpec(
    val alias: String,
    val id: String,
    val versionKey: String,
    val version: String,
) {
    val accessor: String get() = "libs.plugins." + alias.replace('-', '.')
}

/** One line in a module's `dependencies {}` block: a configuration plus a catalog library. */
data class DependencyRef(
    val configuration: String,
    val library: LibrarySpec,
    /** Wrap in `platform(...)` — a BOM import. */
    val isPlatform: Boolean = false,
)

/** A file the template contributes verbatim (sources, manifest, resources, cpp, …). */
data class RecipeFile(val relativePath: String, val content: String)

/** Metadata shown in the template picker; mirrors the domain [com.ahmadkharfan.androidstudiolite.domain.model.ProjectTemplate]. */
data class TemplateMetadata(
    val id: String,
    val name: String,
    val description: String,
    /** Key for the picker's thumbnail artwork; see the domain [ProjectTemplate.thumbnail]. */
    val thumbnail: String,
    val tags: List<String>,
)

/**
 * A project template. Templates never write to disk directly — they populate a [ProjectRecipe], and
 * [ProjectTemplateEngine] renders the shared scaffolding (settings, wrapper, catalog, root build) and
 * flushes everything to disk. This keeps every generated `build.gradle`/catalog uniform and guaranteed
 * parseable by the static Gradle reader.
 *
 * All templates are written fresh for ASL (no GPL sources).
 */
interface Template {
    val metadata: TemplateMetadata

    /** True if this template can be generated in [com.ahmadkharfan.androidstudiolite.domain.model.TemplateLanguage.JAVA]. */
    val supportsJava: Boolean get() = true

    /** Contribute this template's module content into [recipe] for the given [spec]. */
    fun assemble(spec: NewProjectSpec, recipe: ProjectRecipe)
}
