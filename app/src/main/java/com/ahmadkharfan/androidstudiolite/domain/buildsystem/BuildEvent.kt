package com.ahmadkharfan.androidstudiolite.domain.buildsystem

import java.io.File

/** Stream of everything a running build reports, streamed from the remote build backend. */
sealed interface BuildEvent {

    data class Started(val request: BuildRequest) : BuildEvent

    /** Coarse progress, e.g. ":app:compileDebugKotlin" or "Resolving dependencies". */
    data class Progress(val message: String) : BuildEvent

    data class TaskStarted(val taskPath: String) : BuildEvent

    data class TaskFinished(val taskPath: String, val result: TaskResult) : BuildEvent

    /** A raw log line from the build (stdout/stderr of the backend). */
    data class Output(val line: String, val stream: OutputStream) : BuildEvent

    /** A structured problem the UI can surface with file:line navigation. */
    data class Problem(
        val severity: ProblemSeverity,
        val message: String,
        val file: File? = null,
        val line: Int? = null,
        val column: Int? = null,
    ) : BuildEvent

    /** An output the build produced, e.g. the APK/AAB. */
    data class ArtifactProduced(val file: File, val kind: ArtifactKind) : BuildEvent

    data class Finished(val success: Boolean, val durationMillis: Long) : BuildEvent

    enum class TaskResult { SUCCESS, UP_TO_DATE, SKIPPED, FAILED }

    enum class OutputStream { STDOUT, STDERR }

    enum class ProblemSeverity { ERROR, WARNING, INFO }

    enum class ArtifactKind { APK, AAB, OTHER }
}
