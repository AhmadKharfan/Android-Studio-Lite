package com.ahmadkharfan.androidstudiolite.domain.buildsystem

import java.io.File

/** Result of [BuildSystem.sync]: everything the IDE knows about a project's structure. */
data class ProjectModel(
    val name: String,
    val rootDir: File,
    val modules: List<ModuleModel>,
)

data class ModuleModel(
    /** Gradle path, e.g. ":app". */
    val path: String,
    val name: String,
    val type: ModuleType,
    val moduleDir: File,
    val variants: List<VariantModel> = emptyList(),
    val sourceSets: List<SourceSetModel> = emptyList(),
    val dependencies: List<DependencyModel> = emptyList(),
)

enum class ModuleType { ANDROID_APP, ANDROID_LIBRARY, JVM, UNKNOWN }

data class VariantModel(
    /** e.g. "playDebug". */
    val name: String,
    val buildType: String,
    val flavors: List<String> = emptyList(),
)

data class SourceSetModel(
    /** e.g. "main", "debug", "test". */
    val name: String,
    val javaDirs: List<File> = emptyList(),
    val kotlinDirs: List<File> = emptyList(),
    val resDirs: List<File> = emptyList(),
    val assetsDirs: List<File> = emptyList(),
    val manifestFile: File? = null,
)

data class DependencyModel(
    val coordinate: String,
    val scope: DependencyScope,
    /** Set once resolved to a file on disk (jar/aar); null for project deps or unresolved. */
    val resolvedArtifact: File? = null,
)

enum class DependencyScope { IMPLEMENTATION, API, COMPILE_ONLY, RUNTIME_ONLY, TEST, ANDROID_TEST, UNKNOWN }
