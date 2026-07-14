package com.ahmadkharfan.androidstudiolite.data.build

import com.ahmadkharfan.androidstudiolite.build.common.BuildProblem
import com.ahmadkharfan.androidstudiolite.build.common.LogStream
import com.ahmadkharfan.androidstudiolite.build.common.ProblemSeverity
import com.ahmadkharfan.androidstudiolite.build.common.TaskOutcome
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** The engine→domain event bridge maps every callback to the right [BuildEvent]. */
class ChannelReporterTest {

    private val events = mutableListOf<BuildEvent>()
    private val reporter = ChannelReporter { events += it }

    @Test fun `task outcomes map to domain task results`() {
        reporter.taskStarted(":app:dexDebug")
        reporter.taskFinished(":app:dexDebug", TaskOutcome.UP_TO_DATE)
        assertEquals(BuildEvent.TaskStarted(":app:dexDebug"), events[0])
        assertEquals(
            BuildEvent.TaskFinished(":app:dexDebug", BuildEvent.TaskResult.UP_TO_DATE),
            events[1],
        )
    }

    @Test fun `log stream maps to domain output stream`() {
        reporter.log("boom", LogStream.STDERR)
        assertEquals(BuildEvent.Output("boom", BuildEvent.OutputStream.STDERR), events.single())
    }

    @Test fun `problem preserves severity and file location`() {
        reporter.problem(
            BuildProblem(ProblemSeverity.ERROR, "bad", file = File("/x/Foo.kt"), line = 12, column = 3),
        )
        val problem = events.single() as BuildEvent.Problem
        assertEquals(BuildEvent.ProblemSeverity.ERROR, problem.severity)
        assertEquals(12, problem.line)
        assertEquals(3, problem.column)
        assertEquals(File("/x/Foo.kt"), problem.file)
    }

    @Test fun `artifact event carries the apk`() {
        val apk = File("/out/app-debug.apk")
        reporter.artifact(apk)
        assertEquals(BuildEvent.ArtifactProduced(apk, BuildEvent.ArtifactKind.APK), events.single())
    }
}
