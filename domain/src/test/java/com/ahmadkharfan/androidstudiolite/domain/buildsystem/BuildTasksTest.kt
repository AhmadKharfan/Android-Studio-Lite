package com.ahmadkharfan.androidstudiolite.domain.buildsystem

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildTasksTest {

    private fun request(
        modulePath: String = ":app",
        variant: String = "debug",
        kind: BuildKind = BuildKind.ASSEMBLE,
        taskPath: String? = null,
    ) = BuildRequest(File("/p"), modulePath, variant, kind, taskPath = taskPath)

    @Test
    fun `synced task path wins`() {
        assertEquals(
            listOf(":mobile:assembleStagingDebug"),
            BuildTasks.forRequest(request(taskPath = " :mobile:assembleStagingDebug ")),
        )
    }

    @Test
    fun `conventional task is derived from module, kind, and variant`() {
        assertEquals(listOf(":app:assembleStagingDebug"), BuildTasks.forRequest(request(variant = "stagingDebug")))
        assertEquals(listOf(":app:bundleRelease"), BuildTasks.forRequest(request(variant = "release", kind = BuildKind.BUNDLE)))
        assertEquals(listOf(":feature:assembleDebug"), BuildTasks.forRequest(request(modulePath = "feature")))
        assertEquals(listOf("assembleDebug"), BuildTasks.forRequest(request(modulePath = ":")))
    }

    @Test
    fun `clean and model ignore the variant`() {
        assertEquals(listOf("clean"), BuildTasks.forRequest(request(kind = BuildKind.CLEAN)))
        assertEquals(listOf("aslModel"), BuildTasks.forRequest(request(kind = BuildKind.MODEL)))
    }

    @Test
    fun `release variants are recognised by name`() {
        assertTrue(BuildTasks.isReleaseVariant("release"))
        assertTrue(BuildTasks.isReleaseVariant("stagingRelease"))
        assertFalse(BuildTasks.isReleaseVariant("debug"))
        assertFalse(BuildTasks.isReleaseVariant("releaseDebug"))
    }
}
