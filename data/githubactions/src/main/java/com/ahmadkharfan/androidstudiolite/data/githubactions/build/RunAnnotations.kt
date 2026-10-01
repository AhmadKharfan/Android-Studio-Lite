package com.ahmadkharfan.androidstudiolite.data.githubactions.build

import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubApiClient

/**
 * GitHub's own explanations for a run that didn't succeed, taken from its jobs' failure annotations,
 * e.g. "The job was not started because recent account payments have failed or your spending limit
 * needs to be increased." or "Canceling since a higher priority waiting request … exists".
 *
 * They're shown as GitHub wrote them: the app doesn't guess a cause GitHub didn't state.
 */
internal class RunAnnotations(private val api: GitHubApiClient) {

    /** Distinct failure messages for [runId], or empty when GitHub gave none or couldn't be asked. */
    suspend fun failures(handle: BuildHandle, runId: Long): List<String> {
        val jobs = runCatching { api.jobs(handle.owner, handle.repo, runId) }.getOrNull().orEmpty()
        return jobs.mapNotNull { it.checkRunId }
            .flatMap { id -> runCatching { api.checkRunAnnotations(handle.owner, handle.repo, id) }.getOrNull().orEmpty() }
            .filter { it.level == FAILURE }
            .mapNotNull { it.message?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .take(MAX_MESSAGES)
    }

    private companion object {
        const val FAILURE = "failure"
        const val MAX_MESSAGES = 3
    }
}
