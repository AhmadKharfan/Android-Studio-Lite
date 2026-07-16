package com.ahmadkharfan.androidstudiolite.data.remote.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * REST request/response bodies for the control-plane build API (`/v1`), mirroring the app's
 * `PROTOCOL.md`. Field names are lowerCamelCase to match the server; unknown fields are ignored by
 * the shared [RemoteJson] instance so the schema can grow additively.
 */

/**
 * `POST /v1/devices` request. [integrityToken] is an optional Play Integrity token (Phase 4); the
 * server ignores it while `PLAY_INTEGRITY_REQUIRED=false`, so registration works without it.
 */
@Serializable
data class RegisterDeviceRequest(val integrityToken: String? = null)

/** `POST /v1/devices` response — mints an anonymous device token. */
@Serializable
data class RegisterDeviceResponse(val deviceToken: String, val createdAt: Long? = null)

/**
 * `POST /v1/builds` request. Mirrors `BuildRequest`; the source is uploaded (`zip`) or cloned (`git`),
 * not sent here. Field names match the control plane's `CreateBuildRequest` (`variant`, `tasks`); the
 * worker runs `./gradlew <tasks>` and the server names the artifact from `variant`. [signing] carries
 * the user's release keystore material for `assembleRelease`/`bundleRelease` and is transmitted over
 * TLS; it is absent (null) for debug builds. The server ignores it until server-side release signing
 * ships (see the server repo's `docs/secrets.md`), so this is a forward-compatible additive field.
 */
@Serializable
data class CreateBuildRequest(
    val sourceType: String = "zip",
    val gitUrl: String? = null,
    val ref: String? = null,
    val modulePath: String,
    val variant: String,
    val kind: String,
    val tasks: List<String>? = null,
    val signing: SigningMaterial? = null,
    /**
     * SHA-256 (hex) of the source zip about to be uploaded. The server content-addresses the source
     * by it, so a rebuild of an unchanged tree resolves to an object it already has and answers
     * [CreateBuildResponse.sourceUploadRequired]=false — the app then skips the whole upload.
     */
    val sourceHash: String? = null,
    /**
     * A stable id for the project, unchanged across edits (so NOT the source hash). Names the
     * project's Gradle configuration-cache slot server-side, which is what lets a repeat build skip
     * the configuration phase. The server namespaces it per device before a worker sees it.
     */
    val projectKey: String? = null,
)

/**
 * Release-signing material uploaded with a release build. The keystore bytes are base64-encoded and
 * the passwords travel in the request body — sent only over TLS. The server encrypts it at rest,
 * injects it into the per-build sandbox, and wipes it when the build finishes (server `docs/secrets.md`).
 */
@Serializable
data class SigningMaterial(
    /** Base64 (no-wrap) of the keystore file bytes. */
    val keystoreBase64: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
    /** Original keystore file name, for logs/audit only. */
    val keystoreName: String,
)

/** `POST /v1/builds` response — a build id plus (for zip source) a presigned upload URL. */
@Serializable
data class CreateBuildResponse(
    val buildId: String,
    val uploadUrl: String? = null,
    val uploadMethod: String? = "PUT",
    val uploadExpiresAt: Long? = null,
    /**
     * False when the server already has the source for [CreateBuildRequest.sourceHash] and the app
     * must skip the upload. Defaults to TRUE so a missing field (an older server, which never sends
     * it) means "upload", never a silently skipped upload against a server that has nothing.
     */
    val sourceUploadRequired: Boolean = true,
)

/** `POST /v1/builds/{id}/start` and `POST /v1/builds/{id}/cancel` response. */
@Serializable
data class BuildStateResponse(val buildId: String, val status: String)

/**
 * Response of `GET /v1/builds/{id}/artifact`.
 *
 * Matches what the control plane actually sends: `{"url": "...", "expiresInSeconds": 1800}` — a bare
 * presigned GET. It carries no name/kind/size (the name is already known from the `artifactProduced`
 * event, and the URL is single-use). The previous shape here (required `name`, `downloadUrl`) was
 * written against an early draft of PROTOCOL.md and would throw MissingFieldException('name') on
 * every real response.
 */
@Serializable
data class ArtifactResponse(
    val url: String,
    val expiresInSeconds: Long? = null,
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
