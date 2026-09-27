package com.ahmadkharfan.androidstudiolite.data.remote

import com.ahmadkharfan.androidstudiolite.data.remote.protocol.CreateBuildRequest
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.SigningMaterial
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildTasks
import com.ahmadkharfan.androidstudiolite.domain.model.GitRemoteInfo
import com.ahmadkharfan.androidstudiolite.domain.signing.SigningConfig
import java.io.File

internal object RemoteBuildRequestFactory {

    fun eligibleGitSource(source: GitRemoteInfo?): GitRemoteInfo? = source?.takeUnless { it.requiresAuth }

    fun create(
        request: BuildRequest,
        gitSource: GitRemoteInfo?,
        signing: SigningMaterial?,
        sourceHash: String? = null,
        projectKey: String? = null,
    ): CreateBuildRequest {
        val eligibleGit = eligibleGitSource(gitSource)
        return CreateBuildRequest(
            clientRequestId = request.operationId,
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

    fun defaultTasks(request: BuildRequest): List<String> = BuildTasks.forRequest(request)

    fun shouldUseGit(preferGit: Boolean, request: BuildRequest): Boolean =
        preferGit && request.kind != BuildKind.CLEAN

    fun isReleaseVariant(variantName: String): Boolean = BuildTasks.isReleaseVariant(variantName)

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
