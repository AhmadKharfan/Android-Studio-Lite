package com.ahmadkharfan.androidstudiolite.domain.model

enum class AppThemeMode { LIGHT, DARK, SYSTEM }

data class AppPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val editorFontSize: Int = 13,
    val editorThemeId: String = "darcula",
    val editorFontFamily: String = "jetbrains",
    val accentId: String = "emerald",
    val autoOpenLastProject: Boolean = false,
    val editorTabSize: Int = 4,
    val editorAutoSave: Boolean = true,
    val launchAfterInstall: Boolean = true,
    val buildOutputAab: Boolean = false,
    val preferGitSource: Boolean = false,
)
