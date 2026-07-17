package com.ahmadkharfan.androidstudiolite.data.remote

import com.ahmadkharfan.androidstudiolite.data.remote.protocol.CreateBuildRequest
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.SigningMaterial
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.domain.model.GitRemoteInfo
import com.ahmadkharfan.androidstudiolite.domain.signing.SigningConfig
import java.io.File

/**
 * Pure builder for the `POST /v1/builds` body — the source-selection and release-signing policy in one
 * testable place, separate from [RemoteBuildSystem]'s I/O orchestration.
 *
 * - **Source:** a non-null [GitRemoteInfo] selects the `git` path (URL + ref, no upload); null falls
 *   back to `zip` (A2's default). [shouldUseGit] decides whether to even resolve a git source.
 * - **Signing:** [releaseSigningMaterial] turns the user's release keystore into the wire payload,
 *   skipping the auto-managed debug keystore.
 */
internal object RemoteBuildRequestFactory {

    /** Private remotes stay on-device and use zip upload; app credentials never reach the server. */
    fun eligibleGitSource(source: GitRemoteInfo?): GitRemoteInfo? = source?.takeUnless { it.requiresAuth }

    fun create(
        request: BuildRequest,
        gitSource: GitRemoteInfo?,
        signing: SigningMaterial?,
        /** sha256 of the source zip, for server-side upload dedup; null for a git build. */
        sourceHash: String? = null,
        /** Stable project id naming the server's configuration-cache slot for this project. */
        projectKey: String? = null,
    ): CreateBuildRequest {
        val eligibleGit = eligibleGitSource(gitSource)
        return CreateBuildRequest(
            sourceType = if (eligibleGit != null) "git" else "zip",
            gitUrl = eligibleGit?.url,
            ref = eligibleGit?.ref,
            modulePath = request.modulePath,
            variant = request.variantName,
            kind = request.kind.name,
            tasks = defaultTasks(request),
            signing = signing,
            sourceHash = sourceHash,
            projectKey = projectKey,
        )
    }

    /** Gradle tasks for a request: `assemble<Variant>` / `bundle<Variant>` / `clean`. */
    fun defaultTasks(request: BuildRequest): List<String> {
        val variant = request.variantName.replaceFirstChar { it.uppercase() }
        return when (request.kind) {
            BuildKind.ASSEMBLE -> listOf("assemble$variant")
            BuildKind.BUNDLE -> listOf("bundle$variant")
            BuildKind.CLEAN -> listOf("clean")
        }
    }

    /**
     * Whether to attempt a git-source build for [request] given the user's [preferGit] setting. A
     * `clean` never uses git (nothing to clone-build); a resolved remote may still be absent, in which
     * case the caller falls back to zip.
     */
    fun shouldUseGit(preferGit: Boolean, request: BuildRequest): Boolean =
        preferGit && request.kind != BuildKind.CLEAN

    /** True when a variant name denotes a release build that needs the user's release keystore. */
    fun isReleaseVariant(variantName: String): Boolean = variantName.equals("release", ignoreCase = true)

    /**
     * The release-signing payload for [config], or null when there's no usable release keystore (the
     * debug keystore, a missing file, or null config — release builds then fall back to unsigned/debug
     * signing server-side). [readBytes] and [encodeBase64] are injected so this stays JVM-testable.
     */
    fun releaseSigningMaterial(
        config: SigningConfig?,
        readBytes: (File) -> ByteArray? = { it.takeIf(File::isFile)?.readBytes() },
        encodeBase64: (ByteArray) -> String,
    ): SigningMaterial? {
        if (config == null || config.isDebug) return null
        val bytes = readBytes(config.storeFile) ?: return null
        return SigningMaterial(
            keystoreBase64 = encodeBase64(bytes),
            storePassword = config.storePassword,
            keyAlias = config.keyAlias,
            keyPassword = config.keyPassword,
            keystoreName = config.storeFile.name,
        )
    }
}
