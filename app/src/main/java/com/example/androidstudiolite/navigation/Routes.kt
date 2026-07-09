package com.example.androidstudiolite.navigation

object Routes {
    const val ONBOARDING_WELCOME = "onboarding/welcome"
    const val ONBOARDING_STATISTICS = "onboarding/statistics"
    const val ONBOARDING_PERMISSIONS = "onboarding/permissions"
    const val ONBOARDING_SETUP = "onboarding/setup"
    const val ONBOARDING_COMPLETE = "onboarding/complete"

    const val HUB = "hub"
    const val CREATE_PROJECT = "createProject"

    const val EDITOR_PATTERN = "editor/{projectId}"
    fun editor(projectId: String) = "editor/$projectId"

    const val SETTINGS_ROOT = "settings/root"
    const val SETTINGS_GENERAL = "settings/general"
    const val SETTINGS_EDITOR = "settings/editor"
    const val SETTINGS_AI_AGENT = "settings/aiAgent"
    const val SETTINGS_BUILD_RUN = "settings/buildRun"
    const val SETTINGS_ABOUT = "settings/about"
    const val SETTINGS_IDE_CONFIG = "settings/ideConfig"
    const val SETTINGS_DEVELOPER = "settings/developer"

    const val TERMINAL = "terminal"
    const val UI_DESIGNER = "uiDesigner"
    const val CRASH_REPORT = "crashReport"
    const val FOLDER_PICKER = "folderPicker"
    const val ACS_MISSING = "acsMissing"

    const val BLOCKING_ERROR_PATTERN = "blockingError/{type}"
    fun blockingError(type: String) = "blockingError/$type"
}
