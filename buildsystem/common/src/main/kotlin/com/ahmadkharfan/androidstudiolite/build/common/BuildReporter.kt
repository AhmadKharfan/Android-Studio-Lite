package com.ahmadkharfan.androidstudiolite.build.common

import java.io.File

/** Where a raw log line came from. */
enum class LogStream { STDOUT, STDERR }

/** Severity of a structured build problem. */
enum class ProblemSeverity { ERROR, WARNING, INFO }

/** Outcome of running (or skipping) a single task. */
enum class TaskOutcome { SUCCESS, UP_TO_DATE, SKIPPED, FAILED }

/**
 * A structured diagnostic the UI can surface with file:line navigation — the engine-side twin of the
 * app's `BuildEvent.Problem`. Kept here so `:build:engine` never depends on the app's domain types.
 */
data class BuildProblem(
    val severity: ProblemSeverity,
    val message: String,
    val file: File? = null,
    val line: Int? = null,
    val column: Int? = null,
    /** The task that produced it, when known. */
    val taskPath: String? = null,
)

/**
 * Sink for everything a running pipeline reports. The app adapts these callbacks into its
 * `Flow<BuildEvent>`; unit tests use a recording implementation. All methods are no-ops by default so
 * callers only override what they care about.
 */
interface BuildReporter {

    fun progress(message: String) {}

    fun taskStarted(taskPath: String) {}

    fun taskFinished(taskPath: String, outcome: TaskOutcome) {}

    fun log(line: String, stream: LogStream = LogStream.STDOUT) {}

    fun problem(problem: BuildProblem) {}

    /** No-op reporter, for tests and headless runs. */
    object None : BuildReporter
}
