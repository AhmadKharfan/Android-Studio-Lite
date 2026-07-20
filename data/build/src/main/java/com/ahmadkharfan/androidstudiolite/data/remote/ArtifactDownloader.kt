package com.ahmadkharfan.androidstudiolite.data.remote

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import java.io.File
import kotlinx.coroutines.delay

class ArtifactDownloader(
    private val client: RemoteClient,
    private val downloadDir: File,
) {

    data class DownloadedArtifact(val file: File, val kind: BuildEvent.ArtifactKind)

    suspend fun download(buildId: String, fallbackName: String? = null): DownloadedArtifact? {
        var lastError: Throwable? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val artifact = try {
                    client.artifact(buildId)
                } catch (e: RemoteException) {

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
