package com.ahmadkharfan.androidstudiolite.data.build

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildStatus
import com.ahmadkharfan.androidstudiolite.feature.buildrun.reduce
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildConsoleState
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeBuildSystemTest {

    // Zero delay so runTest's virtual clock isn't even needed.
    private val system = FakeBuildSystem(stepDelayMillis = 0)
    private val request = BuildRequest(File("/tmp/demo"), ":app", "debug")

    private fun fold(events: List<BuildEvent>): BuildConsoleState =
        events.fold(BuildConsoleState()) { acc, e -> acc.reduce(e) }

    @Test
    fun `successful build reaches succeeded with an apk artifact`() = runTest {
        val events = system.build(request).toList()
        val state = fold(events)

        assertTrue(events.first() is BuildEvent.Started)
        assertEquals(BuildStatus.Succeeded, state.status)
        assertNotNull(state.artifact)
        assertEquals(BuildEvent.ArtifactKind.APK, state.artifact?.kind)
        assertTrue(state.artifact!!.name.endsWith(".apk"))
        assertTrue("expected task output", state.taskGroups.isNotEmpty())
        assertEquals(0, state.errorCount)
    }

    @Test
    fun `failing build reaches failed with an error problem and no artifact`() = runTest {
        system.failNextBuild = true
        val state = fold(system.build(request).toList())

        assertEquals(BuildStatus.Failed, state.status)
        assertEquals(1, state.errorCount)
        assertEquals("MainActivity.kt", state.problems.single().fileName)
        assertEquals(null, state.artifact)
    }

    @Test
    fun `failNextBuild resets after one build`() = runTest {
        system.failNextBuild = true
        system.build(request).toList()
        val second = fold(system.build(request).toList())
        assertEquals(BuildStatus.Succeeded, second.status)
    }

    @Test
    fun `bundle request produces an aab`() = runTest {
        val req = request.copy(kind = BuildKind.BUNDLE)
        val state = fold(system.build(req).toList())
        assertEquals(BuildEvent.ArtifactKind.AAB, state.artifact?.kind)
        assertTrue(state.artifact!!.name.endsWith(".aab"))
    }
}
