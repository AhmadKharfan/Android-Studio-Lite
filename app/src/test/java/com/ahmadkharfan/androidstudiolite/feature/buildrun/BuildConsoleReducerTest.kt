package com.ahmadkharfan.androidstudiolite.feature.buildrun

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildConsoleReducerTest {

    private val request = BuildRequest(File("/p"), ":app", "debug")

    private fun fold(vararg events: BuildEvent): BuildConsoleState =
        events.fold(BuildConsoleState()) { acc, e -> acc.reduce(e) }

    @Test
    fun `started resets state and marks running`() {
        val state = fold(
            BuildEvent.Output("stale", BuildEvent.OutputStream.STDOUT),
            BuildEvent.Started(request),
        )
        assertEquals(BuildStatus.Running, state.status)
        assertEquals(request, state.request)
        assertTrue(state.logs.isEmpty())
        assertTrue(state.isRunning)
    }

    @Test
    fun `tasks are grouped by module in first-seen order`() {
        val state = fold(
            BuildEvent.Started(request),
            BuildEvent.TaskStarted(":app:preBuild"),
            BuildEvent.TaskStarted(":lib:compileKotlin"),
            BuildEvent.TaskStarted(":app:packageDebug"),
        )
        assertEquals(listOf(":app", ":lib"), state.taskGroups.map { it.module })
        assertEquals(listOf("preBuild", "packageDebug"), state.taskGroups[0].tasks.map { it.name })
    }

    @Test
    fun `task result upserts onto the running line, not a duplicate`() {
        val state = fold(
            BuildEvent.TaskStarted(":app:compileDebugKotlin"),
            BuildEvent.TaskFinished(":app:compileDebugKotlin", BuildEvent.TaskResult.SUCCESS),
        )
        val tasks = state.taskGroups.single().tasks
        assertEquals(1, tasks.size)
        assertEquals(BuildEvent.TaskResult.SUCCESS, tasks.single().result)
        assertEquals(1, state.finishedTaskCount)
    }

    @Test
    fun `finished result is not clobbered by a later running update`() {
        val state = fold(
            BuildEvent.TaskFinished(":app:x", BuildEvent.TaskResult.FAILED),
            BuildEvent.TaskStarted(":app:x"),
        )
        assertEquals(BuildEvent.TaskResult.FAILED, state.taskGroups.single().tasks.single().result)
    }

    @Test
    fun `output separates stdout and stderr`() {
        val state = fold(
            BuildEvent.Output("hello", BuildEvent.OutputStream.STDOUT),
            BuildEvent.Output("boom", BuildEvent.OutputStream.STDERR),
        )
        assertEquals(2, state.logs.size)
        assertTrue(state.logs[1].isError)
    }

    @Test
    fun `problems carry file location and counts`() {
        val state = fold(
            BuildEvent.Problem(BuildEvent.ProblemSeverity.ERROR, "unresolved", File("/p/Main.kt"), 39, 11),
            BuildEvent.Problem(BuildEvent.ProblemSeverity.WARNING, "unused", File("/p/Main.kt"), 3, null),
        )
        assertEquals(1, state.errorCount)
        assertEquals(1, state.warningCount)
        assertEquals("Main.kt:39:11", state.problems[0].location)
        assertEquals("Main.kt:3", state.problems[1].location)
    }

    @Test
    fun `problem with no file has no location`() {
        val state = fold(BuildEvent.Problem(BuildEvent.ProblemSeverity.INFO, "note"))
        assertNull(state.problems.single().location)
    }

    @Test
    fun `finished success records duration and clears progress`() {
        val state = fold(
            BuildEvent.Started(request),
            BuildEvent.Progress("compiling"),
            BuildEvent.ArtifactProduced(File("/p/app.apk"), BuildEvent.ArtifactKind.APK),
            BuildEvent.Finished(success = true, durationMillis = 4200),
        )
        assertEquals(BuildStatus.Succeeded, state.status)
        assertEquals(4200L, state.durationMillis)
        assertNull(state.progressMessage)
        assertEquals("app.apk", state.artifact?.name)
    }

    @Test
    fun `finished failure marks failed`() {
        val state = fold(BuildEvent.Finished(success = false, durationMillis = 10))
        assertEquals(BuildStatus.Failed, state.status)
    }

    @Test
    fun `module-less task path degrades gracefully`() {
        val state = fold(BuildEvent.TaskStarted("help"))
        assertEquals("", state.taskGroups.single().module)
        assertEquals("help", state.taskGroups.single().tasks.single().name)
        assertEquals(BuildKind.ASSEMBLE, request.kind)
    }
}
