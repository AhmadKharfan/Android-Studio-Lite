package com.ahmadkharfan.androidstudiolite.feature.buildrun.api.preflight


enum class PreflightSeverity { INFO, WARNING, BLOCKER }

data class PreflightWarning(
    val severity: PreflightSeverity,
    val title: String,
    val detail: String,
)

data class BuildPreflightResult(val warnings: List<PreflightWarning>) {
    val hasBlocker: Boolean get() = warnings.any { it.severity == PreflightSeverity.BLOCKER }

    val canProceed: Boolean get() = !hasBlocker
}

data class ToolchainVersions(
    val gradle: String? = null,
    val agp: String? = null,
    val jdkMajor: Int? = null,
)

object CompatibilityChecker {

    fun check(versions: ToolchainVersions): List<PreflightWarning> = buildList {
        when (val agpMajor = versions.agp?.majorVersion()) {
            null -> add(missingAgpWarning())
            in 8..Int.MAX_VALUE -> {
                agp8JdkWarning(agpMajor, versions.jdkMajor)?.let { add(it) }
                agp8GradleWarning(agpMajor, versions.gradle)?.let { add(it) }
            }
            7 -> {
                agp7JdkWarning(versions.jdkMajor)?.let { add(it) }
                agp7GradleWarning(versions.gradle)?.let { add(it) }
            }
            else -> add(untestedAgpWarning(versions.agp))
        }
    }

    private fun missingAgpWarning() = PreflightWarning(
        PreflightSeverity.INFO,
        "Couldn't determine AGP version",
        "The Android Gradle Plugin version wasn't found; compatibility can't be pre-checked.",
    )

    private fun agp8JdkWarning(agpMajor: Int, jdk: Int?): PreflightWarning? {
        if (jdk == null || jdk >= 17) return null
        return PreflightWarning(
            PreflightSeverity.WARNING,
            "JDK 17 required",
            "AGP $agpMajor.x needs JDK 17+, but the configured JDK is $jdk. The build will likely fail.",
        )
    }

    private fun agp8GradleWarning(agpMajor: Int, gradle: String?): PreflightWarning? {
        if (gradle == null || compareVersions(gradle, "8.0") >= 0) return null
        return PreflightWarning(
            PreflightSeverity.WARNING,
            "Gradle too old for AGP 8",
            "AGP $agpMajor.x needs Gradle 8.0+, but this project uses Gradle $gradle.",
        )
    }

    private fun agp7JdkWarning(jdk: Int?): PreflightWarning? {
        if (jdk == null || jdk >= 11) return null
        return PreflightWarning(
            PreflightSeverity.WARNING,
            "JDK 11 required",
            "AGP 7.x needs JDK 11+, but the configured JDK is $jdk.",
        )
    }

    private fun agp7GradleWarning(gradle: String?): PreflightWarning? {
        if (gradle == null || compareVersions(gradle, "7.0") >= 0) return null
        return PreflightWarning(
            PreflightSeverity.WARNING,
            "Gradle too old for AGP 7",
            "AGP 7.x needs Gradle 7.0+, but this project uses Gradle $gradle.",
        )
    }

    private fun untestedAgpWarning(agp: String?) = PreflightWarning(
        PreflightSeverity.INFO,
        "Untested AGP version",
        "AGP $agp hasn't been verified with this IDE's toolchain.",
    )
}

object StorageChecker {

    const val LOW_SPACE_BYTES: Long = 250L * 1024 * 1024

    fun check(availableBytes: Long): PreflightWarning? = when {
        availableBytes < LOW_SPACE_BYTES -> PreflightWarning(
            PreflightSeverity.WARNING,
            "Low storage",
            "${availableBytes.toMb()} MB free. The build runs on the server, but the uploaded source " +
                "and the returned APK still need room on this device.",
        )
        else -> null
    }

    private fun Long.toMb(): Long = this / (1024 * 1024)
}

object BuildPreflight {

    fun run(versions: ToolchainVersions, availableBytes: Long): BuildPreflightResult {
        val warnings = buildList {
            StorageChecker.check(availableBytes)?.let { add(it) }
            addAll(CompatibilityChecker.check(versions))
        }
        return BuildPreflightResult(warnings)
    }
}

private fun String.majorVersion(): Int? = trim().substringBefore('.').toIntOrNull()

fun compareVersions(a: String, b: String): Int {
    val pa = a.split('.', '-')
    val pb = b.split('.', '-')
    val n = maxOf(pa.size, pb.size)
    for (i in 0 until n) {
        val x = pa.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
        val y = pb.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
        if (x != y) return x - y
    }
    return 0
}
