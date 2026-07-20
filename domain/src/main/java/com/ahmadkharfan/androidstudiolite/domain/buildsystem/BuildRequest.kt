package com.ahmadkharfan.androidstudiolite.domain.buildsystem

import java.io.File

data class BuildRequest(
    val projectRoot: File,
    val modulePath: String,
    val variantName: String,
    val kind: BuildKind = BuildKind.ASSEMBLE,
    val operationId: String? = null,
    /** Exact task selected from the synchronized Gradle model. */
    val taskPath: String? = null,
    /** Authoritative build type from the synchronized model; avoids name heuristics. */
    val buildType: String? = null,
)

enum class BuildKind { ASSEMBLE, BUNDLE, CLEAN, MODEL }
