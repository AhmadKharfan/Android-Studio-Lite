package com.ahmadkharfan.androidstudiolite.data.remote.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

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
    data class ArtifactProduced(
        val name: String,
        val kind: String,
        val sizeBytes: Long? = null,
        val sha256: String? = null,
        val signed: Boolean? = null,
        val certificateSha256: String? = null,
    ) : WireBuildEvent

    @Serializable
    @SerialName("finished")
    data class Finished(val success: Boolean, val durationMillis: Long) : WireBuildEvent
}

@Serializable
data class WireBuildRequest(
    val buildId: String? = null,
    val modulePath: String,
    val variantName: String,
    val kind: String,
)
