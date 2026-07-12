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
)
