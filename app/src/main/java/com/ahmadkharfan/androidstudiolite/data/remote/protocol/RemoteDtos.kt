package com.ahmadkharfan.androidstudiolite.data.remote.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * REST request/response bodies for the control-plane build API (`/v1`), mirroring the app's
 * `PROTOCOL.md`. Field names are lowerCamelCase to match the server; unknown fields are ignored by
 * the shared [RemoteJson] instance so the schema can grow additively.
 */

/** `POST /v1/devices` request. Attestation is optional (Phase 4). */
@Serializable
data class RegisterDeviceRequest(val attestation: String? = null)

/** `POST /v1/devices` response — mints an anonymous device token. */
@Serializable
data class RegisterDeviceResponse(val deviceToken: String, val createdAt: Long? = null)

/** `POST /v1/builds` request. Mirrors `BuildRequest`; the source is uploaded/cloned, not sent here. */
@Serializable
data class CreateBuildRequest(
    val sourceType: String = "zip",
    val gitUrl: String? = null,
    val ref: String? = null,
    val modulePath: String,
    val variantName: String,
    val kind: String,
    val tasks: List<String>? = null,
)

/** `POST /v1/builds` response — a build id plus (for zip source) a presigned upload URL. */
@Serializable
data class CreateBuildResponse(
    val buildId: String,
    val uploadUrl: String? = null,
    val uploadMethod: String? = "PUT",
    val uploadExpiresAt: Long? = null,
)

/** `POST /v1/builds/{id}/start` and `POST /v1/builds/{id}/cancel` response. */
@Serializable
data class BuildStateResponse(val buildId: String, val status: String)

/** Artifact descriptor from `GET /v1/builds/{id}` and `GET /v1/builds/{id}/artifact`. */
@Serializable
data class ArtifactResponse(
    val kind: String = "OTHER",
    val name: String,
    val sizeBytes: Long? = null,
    val downloadUrl: String? = null,
    val expiresAt: Long? = null,
)

/** `GET /v1/builds/{id}` status poll. */
@Serializable
data class BuildStatusResponse(
    val buildId: String,
    val status: String,
    val queuedAt: Long? = null,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val durationMillis: Long? = null,
    val artifact: ArtifactResponse? = null,
    val error: ErrorBody? = null,
)

/** Non-2xx error envelope: `{ "error": { "code", "message" } }`. */
@Serializable
data class ErrorEnvelope(val error: ErrorBody)

@Serializable
data class ErrorBody(
    val code: String? = null,
    val message: String? = null,
)

/** Wire form of the sync result (`POST /v1/sync`), mirroring `domain/buildsystem/ProjectModel.kt`. */
@Serializable
data class WireProjectModel(
    val name: String,
    val rootDir: String? = null,
    val modules: List<WireModule> = emptyList(),
)

@Serializable
data class WireModule(
    val path: String,
    val name: String,
    val type: String = "UNKNOWN",
    val moduleDir: String? = null,
    val variants: List<WireVariant> = emptyList(),
    val sourceSets: List<WireSourceSet> = emptyList(),
    val dependencies: List<WireDependency> = emptyList(),
)

@Serializable
data class WireVariant(
    val name: String,
    val buildType: String,
    val flavors: List<String> = emptyList(),
)

@Serializable
data class WireSourceSet(
    val name: String,
    val javaDirs: List<String> = emptyList(),
    val kotlinDirs: List<String> = emptyList(),
    val resDirs: List<String> = emptyList(),
    val assetsDirs: List<String> = emptyList(),
    val manifestFile: String? = null,
)

@Serializable
data class WireDependency(
    val coordinate: String,
    @SerialName("scope") val scope: String = "UNKNOWN",
    val resolvedArtifact: String? = null,
)
