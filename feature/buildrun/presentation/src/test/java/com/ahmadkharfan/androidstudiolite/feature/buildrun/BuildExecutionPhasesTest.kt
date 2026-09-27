package com.ahmadkharfan.androidstudiolite.feature.buildrun

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent.RemoteBuildPhase
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.BuildConsoleState
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.BuildExecutionPhase
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.BuildStatus
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.reduce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BuildExecutionPhasesTest {

    @Test
    fun `typed remote phases map to execution phases`() {
        val expected = mapOf(
            RemoteBuildPhase.QUEUED to BuildExecutionPhase.Queued,
            RemoteBuildPhase.PREPARING to BuildExecutionPhase.Preparing,
            RemoteBuildPhase.UPLOADING to BuildExecutionPhase.Preparing,
            RemoteBuildPhase.RUNNING to BuildExecutionPhase.Running,
            RemoteBuildPhase.DOWNLOADING to BuildExecutionPhase.DownloadingArtifact,
        )

        expected.forEach { (remote, execution) ->
            assertEquals(
                remote.name,
                execution,
                nextExecutionPhase(BuildEvent.StatusChanged(remote), BuildExecutionPhase.Running),
            )
        }
    }

    @Test
    fun `typed phase wins over wording that would otherwise be misread`() {
        val event = BuildEvent.StatusChanged(RemoteBuildPhase.QUEUED, "Waiting for a runner to download sources…")

        assertEquals(BuildExecutionPhase.Queued, nextExecutionPhase(event, BuildExecutionPhase.Preparing))
    }

    @Test
    fun `progress text is still interpreted for providers without typed phases`() {
        assertEquals(
            BuildExecutionPhase.DownloadingArtifact,
            nextExecutionPhase(BuildEvent.Progress("Downloading app-debug.apk…"), BuildExecutionPhase.Running),
        )
        assertEquals(
            BuildExecutionPhase.Reconnecting,
            nextExecutionPhase(BuildEvent.Progress("Connection lost (retrying…)"), BuildExecutionPhase.Running),
        )
        assertEquals(
            BuildExecutionPhase.Running,
            nextExecutionPhase(BuildEvent.Progress("Build queued…"), BuildExecutionPhase.Preparing),
        )
    }

    @Test
    fun `timed out build stays timed out when it finishes`() {
        val afterTimeout = nextExecutionPhase(
            BuildEvent.Problem(BuildEvent.ProblemSeverity.ERROR, "Build timed out"),
            BuildExecutionPhase.Running,
        )

        assertEquals(BuildExecutionPhase.TimedOut, afterTimeout)
        assertEquals(BuildExecutionPhase.TimedOut, nextExecutionPhase(BuildEvent.Finished(false, 1), afterTimeout))
    }

    @Test
    fun `status message is shown as progress`() {
        val running = BuildConsoleState(status = BuildStatus.Running, progressMessage = "Preparing…")

        val reduced = running.reduce(BuildEvent.StatusChanged(RemoteBuildPhase.QUEUED, "Queued…"))

        assertEquals("Queued…", reduced.progressMessage)
    }

    @Test
    fun `status without a message leaves the console untouched`() {
        val running = BuildConsoleState(status = BuildStatus.Running, progressMessage = "Preparing…")

        assertSame(running, running.reduce(BuildEvent.StatusChanged(RemoteBuildPhase.RUNNING)))
    }
}
