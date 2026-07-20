package com.ahmadkharfan.androidstudiolite.domain.buildsystem

import java.io.File

data class BuildRequest(
    val projectRoot: File,
    val modulePath: String,
    val variantName: String,
    val kind: BuildKind = BuildKind.ASSEMBLE,
)

enum class BuildKind { ASSEMBLE, BUNDLE, CLEAN }
