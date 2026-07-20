package com.ahmadkharfan.androidstudiolite.domain.buildsystem

import java.io.File

/** A single build invocation handed to [BuildSystem.build]. */
data class BuildRequest(
    val projectRoot: File,
    /** Gradle path of the module to build, e.g. ":app". */
    val modulePath: String,
    /** Variant to build, e.g. "debug" — matched against [VariantModel.name]. */
    val variantName: String,
    val kind: BuildKind = BuildKind.ASSEMBLE,
)

enum class BuildKind { ASSEMBLE, BUNDLE, CLEAN }
