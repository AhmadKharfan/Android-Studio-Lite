package com.ahmadkharfan.androidstudiolite.tooling.server

import com.ahmadkharfan.androidstudiolite.tooling.proto.Events
import com.ahmadkharfan.androidstudiolite.tooling.proto.Notification
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskStartEvent
import org.gradle.tooling.events.task.TaskSuccessResult

/**
 * Bridges the Tooling API's typed task events onto the protocol's `taskStarted` / `taskFinished`
 * events, mapping Gradle's task outcomes to the shared [BuildEvent.TaskResult] names.
 */
class TaskProgressListener(private val emit: (Notification) -> Unit) : ProgressListener {

    override fun statusChanged(event: ProgressEvent) {
        when (event) {
            is TaskStartEvent -> emit(Events.taskStarted(event.descriptor.taskPath))
            is TaskFinishEvent -> emit(Events.taskFinished(event.descriptor.taskPath, resultOf(event)))
        }
    }

    private fun resultOf(event: TaskFinishEvent): String = when (val result = event.result) {
        is TaskSuccessResult -> if (result.isUpToDate) "UP_TO_DATE" else "SUCCESS"
        is TaskSkippedResult -> "SKIPPED"
        is TaskFailureResult -> "FAILED"
        else -> "SUCCESS"
    }
}
