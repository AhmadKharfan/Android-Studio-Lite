package com.ahmadkharfan.androidstudiolite.data.remote

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import java.io.File

/**
 * Fetches a finished build's artifact (APK/AAB) to local storage so the existing install/run flow —
 * [com.ahmadkharfan.androidstudiolite.data.buildsystem.install.ApkInstaller], wired via
 * `BuildRunCoordinator` — can hand it to `PackageInstaller`. The wire `artifactProduced` event only
 * names the object-storage key; this asks the control plane for a fresh presigned GET URL
 * (`GET /v1/builds/{id}/artifact`) and streams the bytes down.
 */
class ArtifactDownloader(
    private val client: RemoteClient,
    /** Where downloaded artifacts land (e.g. `context.cacheDir/build-artifacts`). */
    private val downloadDir: File,
) {

    data class DownloadedArtifact(val file: File, val kind: BuildEvent.ArtifactKind)

    /**
     * Resolves and downloads the artifact for [buildId]. Returns the local file plus its kind, or
     * null if the server reports no downloadable artifact.
     */
    suspend fun download(buildId: String, fallbackName: String? = null): DownloadedArtifact? {
        val artifact = client.artifact(buildId)
        val url = artifact.downloadUrl ?: return null
        val name = artifact.name.ifBlank { fallbackName ?: "$buildId.apk" }
        val dest = File(File(downloadDir, buildId).apply { mkdirs() }, name.substringAfterLast('/'))
        client.download(url, dest)
        return DownloadedArtifact(file = dest, kind = artifactKind(artifact.kind))
    }

    private fun artifactKind(raw: String): BuildEvent.ArtifactKind =
        enumValues<BuildEvent.ArtifactKind>().firstOrNull { it.name == raw } ?: BuildEvent.ArtifactKind.OTHER
}
