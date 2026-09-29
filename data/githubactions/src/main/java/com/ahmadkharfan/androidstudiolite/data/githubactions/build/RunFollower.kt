package com.ahmadkharfan.androidstudiolite.data.githubactions.build

import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubApiClient
import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubApiException
import com.ahmadkharfan.androidstudiolite.data.githubactions.api.WorkflowJob
import com.ahmadkharfan.androidstudiolite.data.githubactions.api.WorkflowRun
import com.ahmadkharfan.androidstudiolite.data.githubactions.workflow.BuildWorkflow
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent.RemoteBuildPhase
import com.ahmadkharfan.androidstudiolite.domain.time.MonotonicClock
import kotlin.math.min

/**
 * Follows a workflow run by polling, translating its state into [BuildEvent]s until it completes.
 *
 * GitHub offers no push channel a phone can use, so the run and its job steps are polled. Requests are
 * conditional (an unchanged run is a free 304), and polling slows down when the account's API budget
 * runs low or GitHub asks to back off.
 */
internal class RunFollower(
    private val api: GitHubApiClient,
    private val clock: MonotonicClock,
    private val wait: suspend (Long) -> Unit,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {

    /** Finds the run started by the dispatch with [handle]'s correlation id. */
    suspend fun resolveRunId(handle: BuildHandle, repository: BuildRepository): Long {
        val deadline = clock.elapsedMillis() + RESOLVE_TIMEOUT_MS
        val title = BuildWorkflow.runName(handle.correlationId)
        while (true) {
            val match = runCatching {
                api.dispatchedRuns(handle.owner, handle.repo, BuildWorkflow.FILE_NAME, repository.defaultBranch)
            }.getOrNull()?.firstOrNull { it.displayTitle == title }
            if (match != null) return match.id
            if (clock.elapsedMillis() >= deadline) throw BuildFailure("GitHub accepted the build but never started it. Try again.")
            wait(RESOLVE_INTERVAL_MS)
        }
    }

    /** Polls run [runId] until it completes or [deadlineMillis] passes, emitting its progress. */
    suspend fun follow(
        handle: BuildHandle,
        runId: Long,
        deadlineMillis: Long,
        liveLog: LiveLog?,
        emit: suspend (BuildEvent) -> Unit,
    ): WorkflowRun {
        val state = FollowState()
        while (true) {
            if (clock.elapsedMillis() >= deadlineMillis) {
                throw BuildFailure("The build took too long on GitHub and was stopped. Try again.")
            }
            val polled = runCatching { api.run(handle.owner, handle.repo, runId) }
            val run = polled.getOrNull()
            if (run == null) {
                wait(backoffAfter(polled.exceptionOrNull(), state, emit))
                continue
            }
            state.consecutiveFailures = 0
            if (run.isCompleted) return run
            report(handle, run, state, emit)
            if (run.status !in QUEUED_STATUSES) liveLog?.poll(emit)
            wait(pollInterval(run))
        }
    }

    private suspend fun report(handle: BuildHandle, run: WorkflowRun, state: FollowState, emit: suspend (BuildEvent) -> Unit) {
        val queued = run.status in QUEUED_STATUSES
        if (run.status != state.lastStatus) {
            state.lastStatus = run.status
            emit(
                if (queued) {
                    BuildEvent.StatusChanged(RemoteBuildPhase.QUEUED, "Waiting for a GitHub build machine…")
                } else {
                    BuildEvent.StatusChanged(RemoteBuildPhase.RUNNING, "Building on GitHub…")
                },
            )
        }
        if (queued) return
        val step = runCatching { api.jobs(handle.owner, handle.repo, run.id) }.getOrNull()?.let(::currentStep)
        if (step != null && step != state.lastStep) {
            state.lastStep = step
            emit(BuildEvent.Progress(stepLabel(step)))
        }
    }

    /** How long to wait before polling again after [error], or throws when the build can't be followed. */
    private suspend fun backoffAfter(error: Throwable?, state: FollowState, emit: suspend (BuildEvent) -> Unit): Long {
        val apiError = error as? GitHubApiException
        val delay = apiError?.let { retryDelay(it, state) }
            ?: throw error ?: BuildFailure("Lost track of the build on GitHub.")
        if (apiError.isTransient && state.consecutiveFailures == 1) {
            emit(BuildEvent.Problem(BuildEvent.ProblemSeverity.INFO, "Connection to GitHub lost (retrying…)"))
        }
        return delay
    }

    private fun retryDelay(error: GitHubApiException, state: FollowState): Long? = when {
        error.retryAfterSeconds != null -> min(error.retryAfterSeconds * 1000, MAX_BACKOFF_MS)
        error.rateLimitResetEpochSeconds != null ->
            ((error.rateLimitResetEpochSeconds - nowEpochSeconds()) * 1000).coerceIn(MIN_POLL_MS, MAX_BACKOFF_MS)
        !error.isTransient -> null
        ++state.consecutiveFailures > MAX_CONSECUTIVE_FAILURES -> null
        else -> min(MIN_POLL_MS shl state.consecutiveFailures, MAX_BACKOFF_MS)
    }

    private fun pollInterval(run: WorkflowRun): Long {
        val remaining = api.lastRateLimit?.remaining
        return when {
            remaining != null && remaining < LOW_BUDGET -> LOW_BUDGET_POLL_MS
            run.status in QUEUED_STATUSES -> QUEUED_POLL_MS
            else -> RUNNING_POLL_MS
        }
    }

    private fun currentStep(jobs: List<WorkflowJob>): String? =
        jobs.firstOrNull { it.status == "in_progress" }
            ?.steps
            ?.firstOrNull { it.status == "in_progress" }
            ?.name

    private fun stepLabel(step: String): String = when (step) {
        "Validate request", "Check out project snapshot", "Verify snapshot" -> "Preparing the build machine…"
        "Set up JDK", "Set up Gradle" -> "Setting up the JDK and Gradle…"
        "Prepare project" -> "Preparing the project…"
        "Build" -> "Running Gradle…"
        "Collect outputs", "Upload result" -> "Packaging the results…"
        else -> "$step…"
    }

    private class FollowState(
        var lastStatus: String? = null,
        var lastStep: String? = null,
        var consecutiveFailures: Int = 0,
    )

    private companion object {
        val QUEUED_STATUSES = setOf("queued", "waiting", "pending", "requested")
        const val RESOLVE_TIMEOUT_MS = 90_000L
        const val RESOLVE_INTERVAL_MS = 3_000L
        const val RUNNING_POLL_MS = 5_000L
        const val QUEUED_POLL_MS = 5_000L
        const val LOW_BUDGET = 500
        const val LOW_BUDGET_POLL_MS = 20_000L
        const val MIN_POLL_MS = 2_000L
        const val MAX_BACKOFF_MS = 60_000L
        const val MAX_CONSECUTIVE_FAILURES = 8
    }
}
