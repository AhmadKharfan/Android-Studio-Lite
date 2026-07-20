package com.ahmadkharfan.androidstudiolite.feature.buildrun

import androidx.compose.runtime.Immutable
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest

/**
 * Flavor-agnostic, folded view of everything a running build has reported so far. A [BuildEvent]
 * stream from either backend ([com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildSystem])
 * is reduced into this by [reduce]; the build console UI renders it directly.
 *
 * Kept as a pure data type + pure reducer so the fold is unit-testable without Android, Compose, or a
 * real build backend (see BuildConsoleReducerTest).
 */
@Immutable
data class BuildConsoleState(
    val status: BuildStatus = BuildStatus.Idle,
    val request: BuildRequest? = null,
    /** Task tree grouped by Gradle module path (":app"), in first-seen order. */
    val taskGroups: List<BuildTaskGroup> = emptyList(),
    /** Raw stdout/stderr log lines, in emission order. */
    val logs: List<BuildLogLine> = emptyList(),
    /** Structured problems with optional file:line for jump-to-source. */
    val problems: List<BuildProblem> = emptyList(),
    val progressMessage: String? = null,
    val artifact: BuildArtifact? = null,
    val durationMillis: Long? = null,
) {
    val isRunning: Boolean get() = status == BuildStatus.Running
    val errorCount: Int get() = problems.count { it.severity == BuildEvent.ProblemSeverity.ERROR }
    val warningCount: Int get() = problems.count { it.severity == BuildEvent.ProblemSeverity.WARNING }

    /** Total task lines across every module — used for the "N tasks" summary and rough progress. */
    val taskCount: Int get() = taskGroups.sumOf { it.tasks.size }
    val finishedTaskCount: Int get() = taskGroups.sumOf { g -> g.tasks.count { it.result != null } }
}

enum class BuildStatus { Idle, Running, Succeeded, Failed, Cancelled }

@Immutable
data class BuildTaskGroup(
    /** Gradle module path, e.g. ":app". Empty for tasks with no module prefix. */
    val module: String,
    val tasks: List<BuildTaskLine>,
)

@Immutable
data class BuildTaskLine(
    /** Full Gradle path, e.g. ":app:compileDebugKotlin". */
    val path: String,
    /** Task name without the module prefix, e.g. "compileDebugKotlin". */
    val name: String,
    /** null while the task is still running. */
    val result: BuildEvent.TaskResult? = null,
)

@Immutable
data class BuildLogLine(
    val text: String,
    val isError: Boolean,
)

@Immutable
data class BuildProblem(
    val severity: BuildEvent.ProblemSeverity,
    val message: String,
    /** Absolute path of the offending file, if the backend reported one. */
    val filePath: String? = null,
    val fileName: String? = null,
    val line: Int? = null,
    val column: Int? = null,
) {
    /** Human-readable "File.kt:39:11" location, or null when the problem isn't tied to a file. */
    val location: String?
        get() = fileName?.let { name ->
            buildString {
                append(name)
                if (line != null) {
                    append(':').append(line)
                    if (column != null) append(':').append(column)
                }
            }
        }
}

@Immutable
data class BuildArtifact(
    val path: String,
    val name: String,
    val kind: BuildEvent.ArtifactKind,
)

/**
 * Folds a single [BuildEvent] into the accumulated [BuildConsoleState]. Pure: no clock, no I/O — the
 * duration comes straight off [BuildEvent.Finished]. Unknown/out-of-order events degrade gracefully
 * (a `TaskFinished` with no prior `TaskStarted` still records the finished task).
 */
fun BuildConsoleState.reduce(event: BuildEvent): BuildConsoleState = when (event) {
    is BuildEvent.Started -> BuildConsoleState(
        status = BuildStatus.Running,
        request = event.request,
        progressMessage = "Starting build…",
    )

    // Persistence hint for the client — does not change console UI state.
    is BuildEvent.RemoteBuildBound -> this

    is BuildEvent.Progress -> copy(progressMessage = event.message)

    is BuildEvent.TaskStarted -> upsertTask(event.taskPath, result = null)

    is BuildEvent.TaskFinished -> upsertTask(event.taskPath, result = event.result)

    is BuildEvent.Output -> copy(
        logs = logs + BuildLogLine(event.line, isError = event.stream == BuildEvent.OutputStream.STDERR),
    )

    is BuildEvent.Problem -> copy(
        problems = problems + BuildProblem(
            severity = event.severity,
            message = event.message,
            filePath = event.file?.path,
            fileName = event.file?.name,
            line = event.line,
            column = event.column,
        ),
    )

    is BuildEvent.ArtifactProduced -> copy(
        artifact = BuildArtifact(event.file.path, event.file.name, event.kind),
    )

    is BuildEvent.Finished -> copy(
        status = if (event.success) BuildStatus.Succeeded else BuildStatus.Failed,
        durationMillis = event.durationMillis,
        progressMessage = null,
    )
}

private fun BuildConsoleState.upsertTask(
    taskPath: String,
    result: BuildEvent.TaskResult?,
): BuildConsoleState {
    val module = taskPath.moduleOf()
    val name = taskPath.taskNameOf()
    val groups = taskGroups.toMutableList()
    val gi = groups.indexOfFirst { it.module == module }
    if (gi < 0) {
        groups += BuildTaskGroup(module, listOf(BuildTaskLine(taskPath, name, result)))
    } else {
        val group = groups[gi]
        val ti = group.tasks.indexOfFirst { it.path == taskPath }
        val tasks = group.tasks.toMutableList()
        if (ti < 0) {
            tasks += BuildTaskLine(taskPath, name, result)
        } else {
            // Preserve a real result over a null (running) update.
            tasks[ti] = tasks[ti].copy(result = result ?: tasks[ti].result)
        }
        groups[gi] = group.copy(tasks = tasks)
    }
    return copy(taskGroups = groups)
}

private fun String.moduleOf(): String = if (contains(':')) substringBeforeLast(':') else ""

private fun String.taskNameOf(): String = substringAfterLast(':')
