package com.ahmadkharfan.androidstudiolite.data.remote

import com.ahmadkharfan.androidstudiolite.data.remote.protocol.ArtifactResponse
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import java.io.File
import java.security.MessageDigest
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

class ArtifactDownloader internal constructor(
    private val downloadDir: File,
    private val fetchArtifact: suspend (String) -> ArtifactResponse,
    private val transferArtifact: suspend (String, File) -> Unit,
    private val waitBeforeRetry: suspend (Long) -> Unit,
) {

    constructor(client: RemoteClient, downloadDir: File) : this(
        downloadDir = downloadDir,
        fetchArtifact = client::artifact,
        transferArtifact = client::download,
        waitBeforeRetry = { delay(it.milliseconds) },
    )

    data class DownloadedArtifact(val file: File, val kind: BuildEvent.ArtifactKind)

    suspend fun download(
        buildId: String,
        fallbackName: String? = null,
        expectedSizeBytes: Long? = null,
        expectedSha256: String? = null,
    ): DownloadedArtifact? {
        val expectation = ArtifactExpectation(
            name = (fallbackName ?: "$buildId.apk").substringAfterLast('/').ifBlank { "$buildId.apk" },
            sizeBytes = expectedSizeBytes,
            sha256 = expectedSha256,
        )
        var lastError: Throwable? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return downloadOnce(buildId, expectation)
            } catch (e: RemoteException) {
                if (e.httpStatus == 404) return null
                lastError = e
            } catch (t: Throwable) {
                lastError = t
            }
            if (attempt < MAX_ATTEMPTS - 1) {
                waitBeforeRetry(BASE_BACKOFF_MS shl attempt)
            }
        }
        throw lastError ?: RemoteException(0, "NETWORK", "Couldn't download the APK")
    }

    private suspend fun downloadOnce(
        buildId: String,
        expectation: ArtifactExpectation,
    ): DownloadedArtifact? {
        val artifact = fetchArtifactOrNull(buildId) ?: return null
        val destination = File(File(downloadDir, buildId).apply { mkdirs() }, expectation.name)
        transferArtifact(artifact.url, destination)
        validateArtifact(destination, expectation)
        return DownloadedArtifact(destination, kindFromName(expectation.name))
    }

    private suspend fun fetchArtifactOrNull(buildId: String): ArtifactResponse? = try {
        fetchArtifact(buildId)
    } catch (exception: RemoteException) {
        if (exception.httpStatus == 404) null else throw exception
    }

    private fun validateArtifact(file: File, expectation: ArtifactExpectation) {
        if (!file.isFile || file.length() == 0L) {
            file.delete()
            throw RemoteException(0, "NETWORK", "Downloaded APK was empty. Retrying…")
        }
        if (expectation.sizeBytes != null && file.length() != expectation.sizeBytes) {
            file.delete()
            throw RemoteException(0, "ARTIFACT_SIZE_MISMATCH", "Downloaded artifact size did not match the build result")
        }
        if (expectation.sha256 != null && !sha256(file).equals(expectation.sha256, ignoreCase = true)) {
            file.delete()
            throw RemoteException(0, "ARTIFACT_CHECKSUM_MISMATCH", "Downloaded artifact failed checksum verification")
        }
    }

    private fun kindFromName(name: String): BuildEvent.ArtifactKind = when {
        name.endsWith(".apk", ignoreCase = true) -> BuildEvent.ArtifactKind.APK
        name.endsWith(".aab", ignoreCase = true) -> BuildEvent.ArtifactKind.AAB
        else -> BuildEvent.ArtifactKind.OTHER
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
        const val BASE_BACKOFF_MS = 1_000L
    }

    private data class ArtifactExpectation(
        val name: String,
        val sizeBytes: Long?,
        val sha256: String?,
    )
}
