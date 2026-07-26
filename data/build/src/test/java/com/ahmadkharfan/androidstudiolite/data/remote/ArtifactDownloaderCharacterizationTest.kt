package com.ahmadkharfan.androidstudiolite.data.remote

import com.ahmadkharfan.androidstudiolite.data.remote.protocol.ArtifactResponse
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArtifactDownloaderCharacterizationTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `successful download preserves sanitized name content and artifact kind`() = runTest {
        val transferredUrls = mutableListOf<String>()
        val downloader = downloader(
            transferArtifact = { url, destination ->
                transferredUrls += url
                destination.writeBytes(byteArrayOf(1, 2, 3))
            },
        )

        val artifact = downloader.download(
            buildId = "build-1",
            fallbackName = "outputs/release/app.AAB",
            expectedSizeBytes = 3,
        )

        assertNotNull(artifact)
        assertEquals("app.AAB", artifact?.file?.name)
        assertEquals(listOf(1, 2, 3), artifact?.file?.readBytes()?.map(Byte::toInt))
        assertEquals(BuildEvent.ArtifactKind.AAB, artifact?.kind)
        assertEquals(listOf(ARTIFACT_URL), transferredUrls)
    }

    @Test
    fun `missing artifact metadata returns null without transfer or retry`() = runTest {
        var transferCount = 0
        val delays = mutableListOf<Long>()
        val downloader = downloader(
            fetchArtifact = { throw RemoteException(404, "NOT_FOUND", "missing") },
            transferArtifact = { _, _ -> transferCount++ },
            waitBeforeRetry = delays::add,
        )

        val artifact = downloader.download("missing-build")

        assertNull(artifact)
        assertEquals(0, transferCount)
        assertEquals(emptyList<Long>(), delays)
    }

    @Test
    fun `checksum mismatch retries five times with exponential delays then deletes file`() = runTest {
        var transferCount = 0
        val delays = mutableListOf<Long>()
        val downloader = downloader(
            transferArtifact = { _, destination ->
                transferCount++
                destination.writeText("wrong")
            },
            waitBeforeRetry = delays::add,
        )

        val error = try {
            downloader.download("build-2", expectedSha256 = "expected")
            null
        } catch (exception: RemoteException) {
            exception
        }

        assertNotNull(error)
        assertEquals("ARTIFACT_CHECKSUM_MISMATCH", error?.code)
        assertEquals(5, transferCount)
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L), delays)
        assertFalse(File(temporaryFolder.root, "build-2/build-2.apk").exists())
    }

    private fun downloader(
        fetchArtifact: suspend (String) -> ArtifactResponse = { ArtifactResponse(ARTIFACT_URL) },
        transferArtifact: suspend (String, File) -> Unit,
        waitBeforeRetry: suspend (Long) -> Unit = {},
    ) = ArtifactDownloader(
        downloadDir = temporaryFolder.root,
        fetchArtifact = fetchArtifact,
        transferArtifact = transferArtifact,
        waitBeforeRetry = waitBeforeRetry,
    )

    private companion object {
        const val ARTIFACT_URL = "https://example.test/artifact"
    }
}
