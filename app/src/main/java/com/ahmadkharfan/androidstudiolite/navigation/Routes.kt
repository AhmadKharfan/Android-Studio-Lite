package com.ahmadkharfan.androidstudiolite.navigation

import com.ahmadkharfan.androidstudiolite.core.navigation.encodeRouteArg
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffTarget

object Routes {

    const val EDITOR_PATTERN = "editor/{projectId}"
    fun editor(projectId: String) = "editor/${encodeRouteArg(projectId)}"

    const val GIT_DIFF_PATTERN = "gitDiff/{projectId}/{target}?path={path}&commitId={commitId}"
    fun gitDiff(projectId: String, path: String, target: GitDiffTarget, commitId: String? = null) =
        "gitDiff/${encodeRouteArg(projectId)}/${target.name}" +
            "?path=${encodeRouteArg(path)}&commitId=${encodeRouteArg(commitId.orEmpty())}"

    const val GIT_HISTORY_PATTERN = "gitHistory/{projectId}?path={path}"
    fun gitHistory(projectId: String, path: String? = null) =
        "gitHistory/${encodeRouteArg(projectId)}?path=${encodeRouteArg(path.orEmpty())}"

    const val GIT_BLAME_PATTERN = "gitBlame/{projectId}?path={path}"
    fun gitBlame(projectId: String, path: String) =
        "gitBlame/${encodeRouteArg(projectId)}?path=${encodeRouteArg(path)}"

    const val GIT_REFS_PATTERN = "gitRefs/{projectId}/{mode}"
    fun gitRefs(projectId: String, mode: String) =
        "gitRefs/${encodeRouteArg(projectId)}/${encodeRouteArg(mode)}"

    const val GIT_CONFLICTS_PATTERN = "gitConflicts/{projectId}"
    fun gitConflicts(projectId: String) = "gitConflicts/${encodeRouteArg(projectId)}"

    const val CRASH_REPORT = "crashReport"
    const val ACS_MISSING = "acsMissing"

    const val BLOCKING_ERROR_PATTERN = "blockingError/{type}"
    fun blockingError(type: String) = "blockingError/${encodeRouteArg(type)}"
}
