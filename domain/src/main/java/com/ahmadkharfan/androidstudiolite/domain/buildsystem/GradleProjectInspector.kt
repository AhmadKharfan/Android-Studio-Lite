package com.ahmadkharfan.androidstudiolite.domain.buildsystem

import java.io.File

/**
 * What a feature needs to know about a Gradle project on disk.
 *
 * Deliberately narrower than the data layer's full parse result: features only ever need the module
 * model and the toolchain versions, so parser-specific types (diagnostics, the version catalog, raw
 * `gradle.properties`) stay inside the data layer instead of leaking across the boundary.
 */
data class GradleProjectSummary(
    val model: ProjectModel,
    val gradleVersion: String? = null,
    val agpVersion: String? = null,
)

interface GradleProjectInspector {

    /** Whether [dir] looks like the root of a Gradle project. */
    fun isGradleProject(dir: File): Boolean

    fun inspect(projectRoot: File): GradleProjectSummary
}
