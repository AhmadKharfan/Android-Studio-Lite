package com.ahmadkharfan.androidstudiolite.data.remote.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class RegisterDeviceRequest(val integrityToken: String? = null)

@Serializable
data class RegisterDeviceResponse(val deviceToken: String, val createdAt: Long? = null)

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
    val sourceHash: String? = null,
    val projectKey: String? = null,
)

@Serializable
data class SigningMaterial(
    val keystoreBase64: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
    val keystoreName: String,
)

@Serializable
data class CreateBuildResponse(
    val buildId: String,
    val uploadUrl: String? = null,
    val uploadMethod: String? = "PUT",
    val uploadExpiresAt: Long? = null,
    val sourceUploadRequired: Boolean = true,
)

@Serializable
data class BuildStateResponse(val buildId: String, val status: String)

@Serializable
data class ArtifactResponse(
    val url: String,
    val expiresInSeconds: Long? = null,
)

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

@Serializable
data class ErrorEnvelope(val error: ErrorBody)

@Serializable
data class ErrorBody(
    val code: String? = null,
    val message: String? = null,
)

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
