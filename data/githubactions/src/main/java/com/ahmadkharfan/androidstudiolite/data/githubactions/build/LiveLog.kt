package com.ahmadkharfan.androidstudiolite.data.githubactions.build

import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubApiClient
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import kotlin.math.min

/**
 * Streams a running build's Gradle output while it runs.
 *
 * The Actions API only serves job logs once a job has finished, so the workflow republishes its log
 * tail to a check run named `asl-live-<correlation id>`: the summary says `asl-lines <n>` (complete lines
 * so far) and the text holds the last of those lines. Each poll emits only the lines not shown yet. If
 * more lines arrived between polls than the tail holds, the gap is announced and [complete] turns
 * false, so the full log can be shown once the build finishes instead.
 */
internal class LiveLog(
    private val api: GitHubApiClient,
    private val handle: BuildHandle,
    private val ref: String,
) {
    /** How many log lines have been emitted so far. */
    var linesShown: Int = 0
        private set

    /** False once some lines could not be shown live. */
    var complete: Boolean = true
        private set

    private var checkRunId: Long? = null

    suspend fun poll(emit: suspend (BuildEvent) -> Unit) {
        val id = checkRunId ?: findCheckRun()?.also { checkRunId = it } ?: return
        val output = runCatching { api.checkRun(handle.owner, handle.repo, id).output }.getOrNull() ?: return
        val total = output.summary?.removePrefix(LINES_PREFIX)?.trim()?.toIntOrNull() ?: return
        if (total <= linesShown) return
        val tail = output.text.orEmpty().removeSuffix("\n").split('\n').filterIndexed { index, line ->
            index > 0 || line.isNotEmpty()
        }
        val fresh = total - linesShown
        if (fresh > tail.size) {
            complete = false
            emit(output("… ${fresh - tail.size} lines not shown live; the full log follows when the build finishes."))
        }
        tail.takeLast(min(fresh, tail.size)).forEach { emit(output(it)) }
        linesShown = total
    }

    private suspend fun findCheckRun(): Long? = runCatching {
        api.checkRunsNamed(handle.owner, handle.repo, ref, "$CHECK_RUN_PREFIX${handle.correlationId}").firstOrNull()?.id
    }.getOrNull()

    private fun output(line: String) = BuildEvent.Output(line, BuildEvent.OutputStream.STDOUT)

    private companion object {
        const val CHECK_RUN_PREFIX = "asl-live-"
        const val LINES_PREFIX = "asl-lines "
    }
}
