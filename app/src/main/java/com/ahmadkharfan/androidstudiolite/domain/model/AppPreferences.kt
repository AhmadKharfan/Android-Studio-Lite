package com.ahmadkharfan.androidstudiolite.domain.model

enum class AppThemeMode { LIGHT, DARK, SYSTEM }

data class AppPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val editorFontSize: Int = 13,
    val editorThemeId: String = "darcula",
    val shareUsageStats: Boolean = false,
    val accentId: String = "emerald",
    val language: String = "en",
    val autoOpenLastProject: Boolean = true,
    val snowfallEasterEgg: Boolean = false,
    val editorTabSize: Int = 4,
    val editorAutoSave: Boolean = true,
    val kotlinLspEnabled: Boolean = true,
    val javaLspEnabled: Boolean = true,
    val xmlLspEnabled: Boolean = false,
    val gradleJvmPath: String = "/data/jdk-17.0.11",
    val parallelTaskExecution: Boolean = true,
    val buildCacheEnabled: Boolean = true,
    val configurationCacheEnabled: Boolean = false,
    val launchAfterInstall: Boolean = true,
    val installViaShizuku: Boolean = false,
    /** When true, release builds produce an Android App Bundle (.aab) instead of an APK. */
    val buildOutputAab: Boolean = false,
    /**
     * When true, a project that has a Git remote builds by sending its remote URL + current branch to
     * the server (no zip upload). Off by default: zip-upload is the safe default (it captures the local
     * working tree, including uncommitted changes) and local-only projects always zip regardless.
     */
    val preferGitSource: Boolean = false,
)
