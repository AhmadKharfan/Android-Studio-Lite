package com.ahmadkharfan.androidstudiolite.domain.buildsystem

import java.io.File

data class ProjectModel(
    val name: String,
    val rootDir: File,
    val modules: List<ModuleModel>,
)

data class ModuleModel(
    val path: String,
    val name: String,
    val type: ModuleType,
    val moduleDir: File,
    val variants: List<VariantModel> = emptyList(),
    val sourceSets: List<SourceSetModel> = emptyList(),
    val dependencies: List<DependencyModel> = emptyList(),
    val applicationId: String? = null,
)

enum class ModuleType { ANDROID_APP, ANDROID_LIBRARY, JVM, UNKNOWN }

data class VariantModel(
    val name: String,
    val buildType: String,
    val flavors: List<String> = emptyList(),
    /** Exact Gradle task paths when supplied by an authoritative model sync. */
    val assembleTaskPath: String? = null,
    val bundleTaskPath: String? = null,
    val debuggable: Boolean? = null,
)

data class SourceSetModel(
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
    val resolvedArtifact: File? = null,
)

enum class DependencyScope { IMPLEMENTATION, API, COMPILE_ONLY, RUNTIME_ONLY, TEST, ANDROID_TEST, UNKNOWN }
