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
     * null if the server reports no downloadable artifact (HTTP 404 → `not_found`).
     *
     * The control plane's artifact response is just `{url, expiresInSeconds}`, so the filename comes
     * from [fallbackName] — the caller passes the name carried by the `artifactProduced` event.
     */
    suspend fun download(buildId: String, fallbackName: String? = null): DownloadedArtifact? {
        val artifact = try {
            client.artifact(buildId)
        } catch (e: RemoteException) {
            // A successful build with no artifact (e.g. a CLEAN task) is not an error.
            if (e.httpStatus == 404) return null else throw e
        }
        val name = (fallbackName ?: "$buildId.apk").substringAfterLast('/').ifBlank { "$buildId.apk" }
        val dest = File(File(downloadDir, buildId).apply { mkdirs() }, name)
        client.download(artifact.url, dest)
        return DownloadedArtifact(file = dest, kind = kindFromName(name))
    }

    /** The wire response carries no kind; infer it from the artifact's extension. */
    private fun kindFromName(name: String): BuildEvent.ArtifactKind = when {
        name.endsWith(".apk", ignoreCase = true) -> BuildEvent.ArtifactKind.APK
        name.endsWith(".aab", ignoreCase = true) -> BuildEvent.ArtifactKind.AAB
        else -> BuildEvent.ArtifactKind.OTHER
    }
}
