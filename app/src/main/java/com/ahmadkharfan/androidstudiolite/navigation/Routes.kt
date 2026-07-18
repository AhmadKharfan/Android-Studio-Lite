package com.ahmadkharfan.androidstudiolite.navigation

import android.net.Uri
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffTarget

object Routes {
    const val ONBOARDING_WELCOME = "onboarding/welcome"
    const val ONBOARDING_PERMISSIONS = "onboarding/permissions"
    const val ONBOARDING_COMPLETE = "onboarding/complete"

    const val HUB = "hub"
    const val CREATE_PROJECT = "createProject"

    const val EDITOR_PATTERN = "editor/{projectId}"
    fun editor(projectId: String) = "editor/$projectId"

    const val GIT_DIFF_PATTERN = "gitDiff/{projectId}/{target}?path={path}&commitId={commitId}"
    fun gitDiff(projectId: String, path: String, target: GitDiffTarget, commitId: String? = null) =
        "gitDiff/${Uri.encode(projectId)}/${target.name}?path=${Uri.encode(path)}&commitId=${Uri.encode(commitId.orEmpty())}"

    const val GIT_HISTORY_PATTERN = "gitHistory/{projectId}?path={path}"
    fun gitHistory(projectId: String, path: String? = null) =
        "gitHistory/${Uri.encode(projectId)}?path=${Uri.encode(path.orEmpty())}"

    const val GIT_BLAME_PATTERN = "gitBlame/{projectId}?path={path}"
    fun gitBlame(projectId: String, path: String) =
        "gitBlame/${Uri.encode(projectId)}?path=${Uri.encode(path)}"

    const val GIT_REFS_PATTERN = "gitRefs/{projectId}/{mode}"
    fun gitRefs(projectId: String, mode: String) = "gitRefs/${Uri.encode(projectId)}/$mode"

    const val GIT_CONFLICTS_PATTERN = "gitConflicts/{projectId}"
    fun gitConflicts(projectId: String) = "gitConflicts/${Uri.encode(projectId)}"

    const val SETTINGS_ROOT = "settings/root"
    const val SETTINGS_GENERAL = "settings/general"
    const val SETTINGS_EDITOR = "settings/editor"
    const val SETTINGS_AI_AGENT = "settings/aiAgent"
    const val SETTINGS_BUILD_RUN = "settings/buildRun"
    const val SETTINGS_SERVER = "settings/server"
    const val SETTINGS_GIT_AUTH = "settings/gitAuth"
    const val SETTINGS_ABOUT = "settings/about"
    const val SETTINGS_IDE_CONFIG = "settings/ideConfig"

    const val TERMINAL = "terminal"
    const val CRASH_REPORT = "crashReport"
    const val FOLDER_PICKER = "folderPicker"
    const val ACS_MISSING = "acsMissing"

    const val BLOCKING_ERROR_PATTERN = "blockingError/{type}"
    fun blockingError(type: String) = "blockingError/$type"
}
