package com.ahmadkharfan.androidstudiolite.domain.buildsystem

import java.io.File

sealed interface BuildEvent {

    data class Started(val request: BuildRequest) : BuildEvent

    data class RemoteBuildBound(val buildId: String) : BuildEvent

    data class Progress(val message: String) : BuildEvent

    /**
     * Where a remote build is in its lifecycle, for providers that know it. Lets consumers track the
     * phase without parsing [Progress] text; [message], when present, is shown like a [Progress].
     */
    data class StatusChanged(val phase: RemoteBuildPhase, val message: String? = null) : BuildEvent

    data class TaskStarted(val taskPath: String) : BuildEvent

    data class TaskFinished(val taskPath: String, val result: TaskResult) : BuildEvent

    data class Output(val line: String, val stream: OutputStream) : BuildEvent

    data class Problem(
        val severity: ProblemSeverity,
        val message: String,
        val file: File? = null,
        val line: Int? = null,
        val column: Int? = null,
    ) : BuildEvent

    data class ArtifactProduced(
        val file: File,
        val kind: ArtifactKind,
        val sizeBytes: Long? = null,
        val sha256: String? = null,
        val signed: Boolean? = null,
        val certificateSha256: String? = null,
    ) : BuildEvent

    data class Finished(val success: Boolean, val durationMillis: Long) : BuildEvent

    enum class RemoteBuildPhase { QUEUED, PREPARING, UPLOADING, RUNNING, DOWNLOADING }

    enum class TaskResult { SUCCESS, UP_TO_DATE, SKIPPED, FAILED }

    enum class OutputStream { STDOUT, STDERR }

    enum class ProblemSeverity { ERROR, WARNING, INFO }

    enum class ArtifactKind { APK, AAB, OTHER }
}
