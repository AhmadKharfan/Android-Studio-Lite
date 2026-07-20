package com.ahmadkharfan.androidstudiolite.feature.buildrun

import androidx.compose.runtime.Immutable
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest

@Immutable
data class BuildConsoleState(
    val status: BuildStatus = BuildStatus.Idle,
    val request: BuildRequest? = null,
    val taskGroups: List<BuildTaskGroup> = emptyList(),
    val logs: List<BuildLogLine> = emptyList(),
    val problems: List<BuildProblem> = emptyList(),
    val progressMessage: String? = null,
    val artifact: BuildArtifact? = null,
    val durationMillis: Long? = null,
) {
    val isRunning: Boolean get() = status == BuildStatus.Running
    val errorCount: Int get() = problems.count { it.severity == BuildEvent.ProblemSeverity.ERROR }
    val warningCount: Int get() = problems.count { it.severity == BuildEvent.ProblemSeverity.WARNING }

    val taskCount: Int get() = taskGroups.sumOf { it.tasks.size }
    val finishedTaskCount: Int get() = taskGroups.sumOf { g -> g.tasks.count { it.result != null } }
}

enum class BuildStatus { Idle, Running, Succeeded, Failed, Cancelled }

@Immutable
data class BuildTaskGroup(
    val module: String,
    val tasks: List<BuildTaskLine>,
)

@Immutable
data class BuildTaskLine(
    val path: String,
    val name: String,
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
    val filePath: String? = null,
    val fileName: String? = null,
    val line: Int? = null,
    val column: Int? = null,
) {
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

fun BuildConsoleState.reduce(event: BuildEvent): BuildConsoleState = when (event) {
    is BuildEvent.Started -> BuildConsoleState(
        status = BuildStatus.Running,
        request = event.request,
        progressMessage = "Starting build…",
    )


    is BuildEvent.RemoteBuildBound -> this

    is BuildEvent.Progress -> copy(progressMessage = event.message)

    is BuildEvent.TaskStarted -> upsertTask(event.taskPath, result = null)

    is BuildEvent.TaskFinished -> upsertTask(event.taskPath, result = event.result)

    is BuildEvent.Output -> copy(
        logs = (logs + BuildLogLine(
            event.line,
            isError = event.stream == BuildEvent.OutputStream.STDERR,
        )).takeLast(MAX_LOG_LINES),
    )

    is BuildEvent.Problem -> copy(
        problems = (problems + BuildProblem(
            severity = event.severity,
            message = event.message,
            filePath = event.file?.path,
            fileName = event.file?.name,
            line = event.line,
            column = event.column,
        )).takeLast(MAX_PROBLEMS),
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

private const val MAX_LOG_LINES = 5_000
private const val MAX_PROBLEMS = 500

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

            tasks[ti] = tasks[ti].copy(result = result ?: tasks[ti].result)
        }
        groups[gi] = group.copy(tasks = tasks)
    }
    return copy(taskGroups = groups)
}

private fun String.moduleOf(): String = if (contains(':')) substringBeforeLast(':') else ""

private fun String.taskNameOf(): String = substringAfterLast(':')
