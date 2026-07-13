package com.ahmadkharfan.androidstudiolite.build.common

import java.io.File

/**
 * One unit of build work with declared, fingerprintable inputs and outputs. The executor uses the
 * declarations to decide up-to-dateness *before* calling [run]; a task that is up-to-date is never
 * run. Keep [run] free of side effects outside the declared [outputFiles] so incremental checks stay
 * correct.
 */
interface BuildTask {

    /** Gradle-style path used in logs and the fingerprint store, e.g. ":app:dexDebug". */
    val path: String

    /** Files whose contents feed this task (sources, jars, the android platform). */
    val inputFiles: List<File>

    /** Non-file inputs that still affect the output (min-sdk, tool versions, flags). */
    val inputValues: List<String>
        get() = emptyList()

    /** Files/directories this task produces; their presence + contents are part of up-to-dateness. */
    val outputFiles: List<File>

    /** Do the work. Throw [BuildFailedException] on failure; poll [TaskContext.throwIfCancelled]. */
    fun run(context: TaskContext)
}

/** Services handed to a task while it runs. */
class TaskContext(
    val reporter: BuildReporter,
    private val cancellation: CancellationToken,
) {
    fun throwIfCancelled() = cancellation.throwIfCancelled()

    fun log(line: String, stream: LogStream = LogStream.STDOUT) = reporter.log(line, stream)

    fun problem(problem: BuildProblem) = reporter.problem(problem)
}
