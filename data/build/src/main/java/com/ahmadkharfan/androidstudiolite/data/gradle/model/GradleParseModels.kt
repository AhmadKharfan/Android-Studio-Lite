package com.ahmadkharfan.androidstudiolite.data.gradle.model


enum class DiagnosticSeverity { INFO, WARNING, ERROR }

data class GradleDiagnostic(
    val message: String,
    val code: String,
    val severity: DiagnosticSeverity = DiagnosticSeverity.WARNING,
    val file: String? = null,
    val offset: Int? = null,
)

enum class GradleDsl { GROOVY, KOTLIN }

data class ParsedSettings(
    val rootProjectName: String?,
    val modulePaths: List<String>,
    val projectDirOverrides: Map<String, String>,
)

data class ParsedPlugin(
    val id: String,
    val version: String? = null,
    val fromCatalog: Boolean = false,
)

enum class RawDependencyKind {
    MODULE,

    CATALOG,

    PROJECT,

    CATALOG_BUNDLE,

    UNKNOWN,
}

data class RawDependency(
    val configuration: String,
    val kind: RawDependencyKind,
    val coordinate: String? = null,
    val catalogAccessor: String? = null,
    val isPlatform: Boolean = false,
)

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

data class ParsedBuildScript(
    val dsl: GradleDsl,
    val plugins: List<ParsedPlugin> = emptyList(),
    val android: ParsedAndroidBlock? = null,
    val dependencies: List<RawDependency> = emptyList(),
)

data class CatalogLibrary(
    val alias: String,
    val group: String?,
    val name: String?,
    val version: String?,
) {
    val coordinate: String?
        get() = when {
            group == null || name == null -> null
            version.isNullOrBlank() -> "$group:$name"
            else -> "$group:$name:$version"
        }
}

data class CatalogPlugin(
    val alias: String,
    val id: String,
    val version: String?,
)

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

    fun findLibrary(accessorAfterLibs: String): CatalogLibrary? =
        libraryByNormalizedAlias[normalizeAlias(accessorAfterLibs)]

    fun findPlugin(accessorAfterPlugins: String): CatalogPlugin? =
        pluginByNormalizedAlias[normalizeAlias(accessorAfterPlugins)]

    fun findBundle(accessorAfterBundles: String): List<String>? =
        bundles.entries.firstOrNull { normalizeAlias(it.key) == normalizeAlias(accessorAfterBundles) }?.value

    companion object {
        fun normalizeAlias(alias: String): String = alias.replace('.', '-').replace('_', '-')
    }
}
