package com.ahmadkharfan.androidstudiolite.build.common

/**
 * Cooperative cancellation for a build. The pipeline polls [throwIfCancelled] at safe points (between
 * tasks and inside long loops); calling [cancel] makes the next poll throw [BuildCancelledException],
 * unwinding the pipeline cleanly. Thread-safe.
 */
class CancellationToken {
    @Volatile
    private var cancelled = false

    val isCancelled: Boolean get() = cancelled

    fun cancel() {
        cancelled = true
    }

    fun throwIfCancelled() {
        if (cancelled) throw BuildCancelledException()
    }
}

/** Thrown to unwind a build when its [CancellationToken] was cancelled. */
class BuildCancelledException : RuntimeException("Build cancelled")

/** A task (or the pipeline) failed; [problems] carries structured diagnostics already reported. */
class BuildFailedException(
    message: String,
    cause: Throwable? = null,
    val problems: List<BuildProblem> = emptyList(),
) : RuntimeException(message, cause)
