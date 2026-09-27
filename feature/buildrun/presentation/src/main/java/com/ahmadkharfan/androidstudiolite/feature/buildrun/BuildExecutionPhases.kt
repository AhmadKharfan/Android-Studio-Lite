package com.ahmadkharfan.androidstudiolite.feature.buildrun

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent.RemoteBuildPhase
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.BuildExecutionPhase

/**
 * The execution phase after [event], given the [current] one.
 *
 * A provider that reports [BuildEvent.StatusChanged] is taken at its word. [BuildEvent.Progress] text
 * is still interpreted for providers that only describe their state in prose.
 */
internal fun nextExecutionPhase(event: BuildEvent, current: BuildExecutionPhase): BuildExecutionPhase =
    when (event) {
        is BuildEvent.Started, is BuildEvent.RemoteBuildBound, is BuildEvent.TaskStarted,
        is BuildEvent.TaskFinished, is BuildEvent.Output -> BuildExecutionPhase.Running
        is BuildEvent.StatusChanged -> executionPhaseOf(event.phase)
        is BuildEvent.Progress -> phaseFromProgressText(event.message)
        is BuildEvent.ArtifactProduced -> BuildExecutionPhase.DownloadingArtifact
        is BuildEvent.Problem ->
            if (event.message.contains("timed out", ignoreCase = true)) BuildExecutionPhase.TimedOut else current
        is BuildEvent.Finished -> finishedPhase(event.success, current)
    }

private fun phaseFromProgressText(message: String): BuildExecutionPhase = when {
    message.contains("download", ignoreCase = true) -> BuildExecutionPhase.DownloadingArtifact
    message.contains("reconnect", ignoreCase = true) ||
        message.contains("retry", ignoreCase = true) -> BuildExecutionPhase.Reconnecting
    else -> BuildExecutionPhase.Running
}

private fun finishedPhase(success: Boolean, current: BuildExecutionPhase): BuildExecutionPhase = when {
    current == BuildExecutionPhase.TimedOut -> current
    success -> BuildExecutionPhase.Succeeded
    else -> BuildExecutionPhase.Failed
}

private fun executionPhaseOf(phase: RemoteBuildPhase): BuildExecutionPhase = when (phase) {
    RemoteBuildPhase.QUEUED -> BuildExecutionPhase.Queued
    RemoteBuildPhase.PREPARING, RemoteBuildPhase.UPLOADING -> BuildExecutionPhase.Preparing
    RemoteBuildPhase.RUNNING -> BuildExecutionPhase.Running
    RemoteBuildPhase.DOWNLOADING -> BuildExecutionPhase.DownloadingArtifact
}
