package com.ahmadkharfan.androidstudiolite.data.remote.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Wire form of [com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent], as emitted by the
 * server control plane over `WS /v1/builds/{id}/stream`. One JSON object per frame, tagged by a
 * lowerCamelCase `"type"` discriminator (the server is the source of truth — see the app's
 * `PROTOCOL.md` and the server's `control-plane/PROTOCOL.md`).
 *
 * The domain model holds `java.io.File`; on the wire paths are project-relative POSIX strings and
 * enums are their Kotlin constant names. Mapping back to the domain (and reattaching the local
 * project root) is [BuildEventMapper.toDomain]. Unknown `type` values and unknown fields are
 * tolerated by [BuildEventParser] so the schema can grow additively.
 */
@Serializable
@JsonClassDiscriminator("type")
sealed interface WireBuildEvent {

    @Serializable
    @SerialName("started")
    data class Started(val request: WireBuildRequest) : WireBuildEvent

    @Serializable
    @SerialName("progress")
    data class Progress(val message: String) : WireBuildEvent

    @Serializable
    @SerialName("taskStarted")
    data class TaskStarted(val taskPath: String) : WireBuildEvent

    @Serializable
    @SerialName("taskFinished")
    data class TaskFinished(val taskPath: String, val result: String) : WireBuildEvent

    @Serializable
    @SerialName("output")
    data class Output(val line: String, val stream: String) : WireBuildEvent

    @Serializable
    @SerialName("problem")
    data class Problem(
        val severity: String,
        val message: String,
        val file: String? = null,
        val line: Int? = null,
        val column: Int? = null,
    ) : WireBuildEvent

    @Serializable
    @SerialName("artifactProduced")
    data class ArtifactProduced(val name: String, val kind: String) : WireBuildEvent

    @Serializable
    @SerialName("finished")
    data class Finished(val success: Boolean, val durationMillis: Long) : WireBuildEvent
}

/** Wire form of a build request as echoed back inside a `started` event. */
@Serializable
data class WireBuildRequest(
    val buildId: String? = null,
    val modulePath: String,
    val variantName: String,
    val kind: String,
)
