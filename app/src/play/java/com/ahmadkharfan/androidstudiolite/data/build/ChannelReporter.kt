package com.ahmadkharfan.androidstudiolite.data.build

import com.ahmadkharfan.androidstudiolite.build.common.BuildProblem
import com.ahmadkharfan.androidstudiolite.build.common.BuildReporter
import com.ahmadkharfan.androidstudiolite.build.common.LogStream
import com.ahmadkharfan.androidstudiolite.build.common.ProblemSeverity
import com.ahmadkharfan.androidstudiolite.build.common.TaskOutcome
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import java.io.File

/**
 * Adapts the engine's [BuildReporter] callbacks into the app's shared [BuildEvent] flow. Kept in the
 * play source set because it bridges the two type worlds — the engine's platform-neutral reporting
 * and the domain's `BuildEvent` — so neither module depends on the other's enums.
 */
internal class ChannelReporter(private val emit: (BuildEvent) -> Unit) : BuildReporter {

    override fun progress(message: String) = emit(BuildEvent.Progress(message))

    override fun taskStarted(taskPath: String) = emit(BuildEvent.TaskStarted(taskPath))

    override fun taskFinished(taskPath: String, outcome: TaskOutcome) =
        emit(BuildEvent.TaskFinished(taskPath, outcome.toDomain()))

    override fun log(line: String, stream: LogStream) = emit(BuildEvent.Output(line, stream.toDomain()))

    override fun problem(problem: BuildProblem) = emit(problem.toDomain())

    /** Emit an artifact-produced event for the built APK. */
    fun artifact(file: File) = emit(BuildEvent.ArtifactProduced(file, BuildEvent.ArtifactKind.APK))

    private fun TaskOutcome.toDomain(): BuildEvent.TaskResult = when (this) {
        TaskOutcome.SUCCESS -> BuildEvent.TaskResult.SUCCESS
        TaskOutcome.UP_TO_DATE -> BuildEvent.TaskResult.UP_TO_DATE
        TaskOutcome.SKIPPED -> BuildEvent.TaskResult.SKIPPED
        TaskOutcome.FAILED -> BuildEvent.TaskResult.FAILED
    }

    private fun LogStream.toDomain(): BuildEvent.OutputStream = when (this) {
        LogStream.STDOUT -> BuildEvent.OutputStream.STDOUT
        LogStream.STDERR -> BuildEvent.OutputStream.STDERR
    }

    private fun BuildProblem.toDomain(): BuildEvent.Problem = BuildEvent.Problem(
        severity = when (severity) {
            ProblemSeverity.ERROR -> BuildEvent.ProblemSeverity.ERROR
            ProblemSeverity.WARNING -> BuildEvent.ProblemSeverity.WARNING
            ProblemSeverity.INFO -> BuildEvent.ProblemSeverity.INFO
        },
        message = message,
        file = file,
        line = line,
        column = column,
    )
}

/** Factory used by [InProcessBuildSystem] so `emit` can route through `trySendBlocking`. */
internal fun channelReporter(emit: (BuildEvent) -> Unit): ChannelReporter = ChannelReporter(emit)
