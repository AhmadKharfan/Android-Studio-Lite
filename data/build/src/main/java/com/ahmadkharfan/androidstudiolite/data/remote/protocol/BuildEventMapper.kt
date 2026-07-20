package com.ahmadkharfan.androidstudiolite.data.remote.protocol

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import java.io.File

object BuildEventMapper {

    fun toDomain(wire: WireBuildEvent, projectRoot: File): BuildEvent = when (wire) {
        is WireBuildEvent.Started -> BuildEvent.Started(
            BuildRequest(
                projectRoot = projectRoot,
                modulePath = wire.request.modulePath,
                variantName = wire.request.variantName,
                kind = buildKind(wire.request.kind),
            ),
        )

        is WireBuildEvent.Progress -> BuildEvent.Progress(wire.message)

        is WireBuildEvent.TaskStarted -> BuildEvent.TaskStarted(wire.taskPath)

        is WireBuildEvent.TaskFinished -> BuildEvent.TaskFinished(wire.taskPath, taskResult(wire.result))

        is WireBuildEvent.Output -> BuildEvent.Output(wire.line, outputStream(wire.stream))

        is WireBuildEvent.Problem -> BuildEvent.Problem(
            severity = severity(wire.severity),
            message = wire.message,
            file = wire.file?.let { File(projectRoot, it) },
            line = wire.line,
            column = wire.column,
        )


        is WireBuildEvent.ArtifactProduced -> BuildEvent.ArtifactProduced(
            file = File(wire.name),
            kind = artifactKind(wire.kind),
            sizeBytes = wire.sizeBytes,
            sha256 = wire.sha256,
            signed = wire.signed,
            certificateSha256 = wire.certificateSha256,
        )

        is WireBuildEvent.Finished -> BuildEvent.Finished(wire.success, wire.durationMillis)
    }

    private fun buildKind(raw: String): BuildKind =
        enumValues<BuildKind>().firstOrNull { it.name == raw } ?: BuildKind.ASSEMBLE

    private fun taskResult(raw: String): BuildEvent.TaskResult =
        enumValues<BuildEvent.TaskResult>().firstOrNull { it.name == raw } ?: BuildEvent.TaskResult.SUCCESS

    private fun outputStream(raw: String): BuildEvent.OutputStream =
        enumValues<BuildEvent.OutputStream>().firstOrNull { it.name == raw } ?: BuildEvent.OutputStream.STDOUT

    private fun severity(raw: String): BuildEvent.ProblemSeverity =
        enumValues<BuildEvent.ProblemSeverity>().firstOrNull { it.name == raw } ?: BuildEvent.ProblemSeverity.INFO

    private fun artifactKind(raw: String): BuildEvent.ArtifactKind =
        enumValues<BuildEvent.ArtifactKind>().firstOrNull { it.name == raw } ?: BuildEvent.ArtifactKind.OTHER
}
