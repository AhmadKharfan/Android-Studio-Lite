package com.example.androidstudiolite.domain.model

/**
 * The on-device toolchain a real Gradle build needs — a JDK, the Android SDK (platform +
 * build-tools), and Gradle itself. See docs/build-run/06-full-build-production-study.md.
 */
enum class IdeEnvironmentComponentStatus { NotInstalled, Downloading, Verifying, Extracting, Installed, Failed }

data class IdeEnvironmentComponentState(
    val id: String,
    val displayName: String,
    val version: String,
    val sizeBytes: Long,
    val status: IdeEnvironmentComponentStatus,
    val downloadedBytes: Long = 0,
    val errorMessage: String? = null,
) {
    val progressPercent: Int
        get() = if (sizeBytes <= 0) 0 else ((downloadedBytes * 100) / sizeBytes).toInt().coerceIn(0, 100)
}

data class IdeEnvironmentState(
    val abi: String?,
    val components: List<IdeEnvironmentComponentState> = emptyList(),
) {
    val allInstalled: Boolean
        get() = components.isNotEmpty() && components.all { it.status == IdeEnvironmentComponentStatus.Installed }
    val isInstalling: Boolean
        get() = components.any {
            it.status == IdeEnvironmentComponentStatus.Downloading ||
                it.status == IdeEnvironmentComponentStatus.Verifying ||
                it.status == IdeEnvironmentComponentStatus.Extracting
        }
    val failed: Boolean
        get() = components.any { it.status == IdeEnvironmentComponentStatus.Failed }
}
