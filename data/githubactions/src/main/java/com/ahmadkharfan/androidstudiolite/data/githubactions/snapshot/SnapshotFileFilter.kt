package com.ahmadkharfan.androidstudiolite.data.githubactions.snapshot

import java.io.File

/**
 * Which files of a project go into a build snapshot.
 *
 * Mirrors what the server backend uploads: tool and output directories stay behind, but a directory
 * named `build` is kept when it is itself a Gradle module (e.g. `:build`) rather than Gradle output.
 * `local.properties` is left out as well: it holds machine paths and often private keys, and the
 * build runner provides its own SDK.
 */
internal object SnapshotFileFilter {

    private val TOOL_DIRS = setOf(".gradle", ".idea", ".git", ".kotlin", ".cxx")
    private val MACHINE_FILES = setOf("local.properties")

    fun includesDirectory(dir: File): Boolean = when (dir.name) {
        in TOOL_DIRS -> false
        "build" -> !isGradleOutput(dir)
        else -> true
    }

    fun includesFile(root: File, file: File): Boolean =
        !(file.name in MACHINE_FILES && file.parentFile?.absoluteFile == root.absoluteFile)

    private fun isGradleOutput(dir: File): Boolean {
        val parent = dir.parentFile ?: return false
        return isGradleProject(parent) && !isGradleModule(dir)
    }

    private fun isGradleProject(dir: File): Boolean =
        listOf("build.gradle.kts", "build.gradle", "settings.gradle.kts", "settings.gradle").any { File(dir, it).isFile }

    private fun isGradleModule(dir: File): Boolean = isGradleProject(dir) || File(dir, "src").isDirectory
}
