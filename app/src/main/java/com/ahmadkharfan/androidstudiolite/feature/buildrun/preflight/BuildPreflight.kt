package com.ahmadkharfan.androidstudiolite.feature.buildrun.preflight

/**
 * Pre-build reliability checks from docs/build-run/10 §3 (Gap 6): version-compatibility and
 * storage-pressure. Pure and unit-testable; the Android layer feeds in real device values (free bytes
 * from `StatFs`, versions from the T8 Gradle parse) and surfaces the resulting [PreflightWarning]s
 * before kicking off [com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildSystem.build].
 */

enum class PreflightSeverity { INFO, WARNING, BLOCKER }

data class PreflightWarning(
    val severity: PreflightSeverity,
    val title: String,
    val detail: String,
)

data class BuildPreflightResult(val warnings: List<PreflightWarning>) {
    val hasBlocker: Boolean get() = warnings.any { it.severity == PreflightSeverity.BLOCKER }

    /** A build may proceed unless something is an outright blocker (e.g. no storage at all). */
    val canProceed: Boolean get() = !hasBlocker
}

/** Versions read from the project (gradle-wrapper.properties, AGP plugin) and the installed JDK. */
data class ToolchainVersions(
    val gradle: String? = null,
    val agp: String? = null,
    val jdkMajor: Int? = null,
)

/**
 * Checks the classic Gradle ↔ AGP ↔ JDK triple — doc 10 calls a mismatch here "the single biggest
 * real-world failure". Rules are a conservative subset of the AGP compatibility matrix; anything we
 * can't determine is reported as INFO, never a false blocker.
 */
object CompatibilityChecker {

    fun check(versions: ToolchainVersions): List<PreflightWarning> {
        val warnings = mutableListOf<PreflightWarning>()
        val agpMajor = versions.agp?.majorVersion()
        val gradle = versions.gradle
        val jdk = versions.jdkMajor

        when (agpMajor) {
            null -> warnings += PreflightWarning(
                PreflightSeverity.INFO,
                "Couldn't determine AGP version",
                "The Android Gradle Plugin version wasn't found; compatibility can't be pre-checked.",
            )
            in 8..Int.MAX_VALUE -> {
                if (jdk != null && jdk < 17) {
                    warnings += PreflightWarning(
                        PreflightSeverity.WARNING,
                        "JDK 17 required",
                        "AGP $agpMajor.x needs JDK 17+, but the configured JDK is $jdk. The build will likely fail.",
                    )
                }
                if (gradle != null && compareVersions(gradle, "8.0") < 0) {
                    warnings += PreflightWarning(
                        PreflightSeverity.WARNING,
                        "Gradle too old for AGP 8",
                        "AGP $agpMajor.x needs Gradle 8.0+, but this project uses Gradle $gradle.",
                    )
                }
            }
            7 -> {
                if (jdk != null && jdk < 11) {
                    warnings += PreflightWarning(
                        PreflightSeverity.WARNING,
                        "JDK 11 required",
                        "AGP 7.x needs JDK 11+, but the configured JDK is $jdk.",
                    )
                }
                if (gradle != null && compareVersions(gradle, "7.0") < 0) {
                    warnings += PreflightWarning(
                        PreflightSeverity.WARNING,
                        "Gradle too old for AGP 7",
                        "AGP 7.x needs Gradle 7.0+, but this project uses Gradle $gradle.",
                    )
                }
            }
            else -> warnings += PreflightWarning(
                PreflightSeverity.INFO,
                "Untested AGP version",
                "AGP ${versions.agp} hasn't been verified with this IDE's toolchain.",
            )
        }
        return warnings
    }
}

/**
 * Warns when the device is too full to stage a build.
 *
 * These thresholds used to assume the multi-gigabyte on-device `.gradle` cache and build outputs, and
 * blocked the build below 250 MB. The build now runs on the remote worker: the device only needs room
 * for the source zip going up and the APK coming back, tens of megabytes. So this never blocks — a
 * full device gets a warning it can build through, and a genuinely fatal write failure surfaces from
 * the packager with a real error instead of a guess made up-front.
 */
object StorageChecker {

    const val LOW_SPACE_BYTES: Long = 250L * 1024 * 1024 // 250 MB

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

/** Runs every pre-build check and aggregates the warnings. */
object BuildPreflight {

    fun run(versions: ToolchainVersions, availableBytes: Long): BuildPreflightResult {
        val warnings = buildList {
            StorageChecker.check(availableBytes)?.let { add(it) }
            addAll(CompatibilityChecker.check(versions))
        }
        return BuildPreflightResult(warnings)
    }
}

/** Leading integer of a version string, e.g. "8.2.1" -> 8; null if not parseable. */
private fun String.majorVersion(): Int? = trim().substringBefore('.').toIntOrNull()

/**
 * Compares dotted numeric version strings. Returns <0, 0, >0 like [Comparable]. Non-numeric trailing
 * qualifiers (e.g. "8.0-rc-1") are ignored beyond the numeric prefix of each segment.
 */
internal fun compareVersions(a: String, b: String): Int {
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
