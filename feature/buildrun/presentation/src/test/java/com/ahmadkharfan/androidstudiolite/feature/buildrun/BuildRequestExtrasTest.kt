package com.ahmadkharfan.androidstudiolite.feature.buildrun

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuildRequestExtrasTest {

    private fun roundTrip(request: BuildRequest, operationId: String = "op-1"): BuildRequest? {
        val extras = mutableMapOf<String, String>()
        BuildRequestExtras.write(request) { key, value -> extras[key] = value }
        return BuildRequestExtras.read(operationId, extras::get)
    }

    @Test
    fun `exact task and build type survive the service intent`() {
        val request = BuildRequest(
            projectRoot = File("/projects/demo").absoluteFile,
            modulePath = ":mobile",
            variantName = "stagingRelease",
            kind = BuildKind.BUNDLE,
            operationId = "op-1",
            taskPath = ":mobile:bundleStagingRelease",
            buildType = "release",
        )

        assertEquals(request, roundTrip(request))
    }

    @Test
    fun `absent task and build type stay absent`() {
        val request = BuildRequest(
            projectRoot = File("/projects/demo").absoluteFile,
            modulePath = ":app",
            variantName = "debug",
            operationId = "op-2",
        )

        val restored = roundTrip(request, operationId = "op-2")

        assertNull(restored?.taskPath)
        assertNull(restored?.buildType)
        assertEquals(request, restored)
    }

    @Test
    fun `operation id comes from the caller, not the extras`() {
        val request = BuildRequest(File("/projects/demo").absoluteFile, ":app", "debug", operationId = "stale")

        assertEquals("fresh", roundTrip(request, operationId = "fresh")?.operationId)
    }

    @Test
    fun `missing project root cannot be executed`() {
        assertNull(BuildRequestExtras.read("op") { null })
    }

    @Test
    fun `blank module and variant fall back to the app debug defaults`() {
        val restored = BuildRequestExtras.read("op") { key ->
            if (key == RemoteBuildKeepAliveService.EXTRA_PROJECT_ROOT) "/projects/demo" else ""
        }

        assertEquals(":app", restored?.modulePath)
        assertEquals("debug", restored?.variantName)
        assertEquals(BuildKind.ASSEMBLE, restored?.kind)
    }
}
