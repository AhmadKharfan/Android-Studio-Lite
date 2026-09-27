package com.ahmadkharfan.androidstudiolite.domain.buildsystem

/** How a [BuildRequest] translates to Gradle, independent of which provider runs it. */
object BuildTasks {

    /** The Gradle task paths to run for [request]: the exact synced task when known, else the conventional one. */
    fun forRequest(request: BuildRequest): List<String> {
        request.taskPath?.trim()?.takeIf { it.isNotEmpty() }?.let { return listOf(it) }
        val variant = request.variantName.replaceFirstChar { it.uppercase() }
        val taskName = when (request.kind) {
            BuildKind.ASSEMBLE -> "assemble$variant"
            BuildKind.BUNDLE -> "bundle$variant"
            BuildKind.CLEAN -> return listOf("clean")
            BuildKind.MODEL -> return listOf("aslModel")
        }
        val module = request.modulePath.trim()
            .takeIf { it.isNotEmpty() && it != ":" }
            ?.let { if (it.startsWith(":")) it else ":$it" }
        return listOf(if (module != null) "$module:$taskName" else taskName)
    }

    /** Best guess from the variant name alone, for when the synced build type is unknown. */
    fun isReleaseVariant(variantName: String): Boolean =
        variantName.trim().let { name ->
            name.equals("release", ignoreCase = true) ||
                (!name.contains("debug", ignoreCase = true) && name.endsWith("release", ignoreCase = true))
        }
}

