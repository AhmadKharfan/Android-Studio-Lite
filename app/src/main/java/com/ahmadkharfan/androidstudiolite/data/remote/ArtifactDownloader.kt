package com.ahmadkharfan.androidstudiolite.data.remote

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import java.io.File
import kotlinx.coroutines.delay

/**
 * Fetches a finished build's artifact (APK/AAB) to local storage so the existing install/run flow —
 * [com.ahmadkharfan.androidstudiolite.data.buildsystem.install.ApkInstaller], wired via
 * `BuildRunCoordinator` — can hand it to `PackageInstaller`. The wire `artifactProduced` event only
 * names the object-storage key; this asks the control plane for a fresh presigned GET URL
 * (`GET /v1/builds/{id}/artifact`) and streams the bytes down.
 *
 * Retries with a **fresh** presigned URL each attempt: Spaces may not be readable the instant the
 * worker finishes, and a stale URL after a transient failure must not be reused.
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
        var lastError: Throwable? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val artifact = try {
                    client.artifact(buildId)
                } catch (e: RemoteException) {
                    // A successful build with no artifact (e.g. a CLEAN task) is not an error.
                    if (e.httpStatus == 404) return null else throw e
                }
                val name = (fallbackName ?: "$buildId.apk").substringAfterLast('/').ifBlank { "$buildId.apk" }
                val dest = File(File(downloadDir, buildId).apply { mkdirs() }, name)
                client.download(artifact.url, dest)
                if (!dest.isFile || dest.length() == 0L) {
                    dest.delete()
                    throw RemoteException(0, "NETWORK", "Downloaded APK was empty — retrying…")
                }
                return DownloadedArtifact(file = dest, kind = kindFromName(name))
            } catch (e: RemoteException) {
                if (e.httpStatus == 404) return null
                lastError = e
            } catch (t: Throwable) {
                lastError = t
            }
            if (attempt < MAX_ATTEMPTS - 1) {
                delay(BASE_BACKOFF_MS shl attempt)
            }
        }
        throw lastError ?: RemoteException(0, "NETWORK", "Couldn't download the APK")
    }

    /** The wire response carries no kind; infer it from the artifact's extension. */
    private fun kindFromName(name: String): BuildEvent.ArtifactKind = when {
        name.endsWith(".apk", ignoreCase = true) -> BuildEvent.ArtifactKind.APK
        name.endsWith(".aab", ignoreCase = true) -> BuildEvent.ArtifactKind.AAB
        else -> BuildEvent.ArtifactKind.OTHER
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
        const val BASE_BACKOFF_MS = 1_000L
    }
}
