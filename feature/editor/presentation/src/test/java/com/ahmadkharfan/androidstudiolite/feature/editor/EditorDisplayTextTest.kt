package com.ahmadkharfan.androidstudiolite.feature.editor

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.BuildConsoleState
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.BuildLogLine
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.BuildProblem
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.BuildTaskGroup
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.BuildTaskLine
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorDisplayTextTest {
    @Test
    fun `file entry name without dot whitespace is unchanged`() {
        assertEquals("Main.kt", sanitizeFileEntryName("Main.kt"))
    }

    @Test
    fun `file entry name collapses space after dot`() {
        assertEquals("Main.kt", sanitizeFileEntryName("Main. kt"))
    }

    @Test
    fun `file entry name sanitizes multiple dot whitespace occurrences`() {
        assertEquals("one.two.three", sanitizeFileEntryName("one. two. three"))
    }

    @Test
    fun `file entry name collapses several spaces and tabs after dot`() {
        assertEquals("one.two.three", sanitizeFileEntryName("one.   two.\tthree"))
    }

    @Test
    fun `bare dot is unchanged`() {
        assertEquals(".", sanitizeFileEntryName("."))
    }

    @Test
    fun `empty file entry name is unchanged`() {
        assertEquals("", sanitizeFileEntryName(""))
    }

    @Test
    fun `variant label capitalizes lowercase initial`() {
        assertEquals("Debug", variantLabel("debug"))
    }

    @Test
    fun `variant label preserves capitalized input`() {
        assertEquals("Debug", variantLabel("Debug"))
    }

    @Test
    fun `empty variant label remains empty`() {
        assertEquals("", variantLabel(""))
    }

    @Test
    fun `gradle task name capitalizes debug variant`() {
        assertEquals("assembleDebug", gradleAssembleTaskName("debug"))
    }

    @Test
    fun `gradle task name capitalizes flavored variant`() {
        assertEquals("assembleGplayDebug", gradleAssembleTaskName("gplayDebug"))
    }

    @Test
    fun `clipboard text serializes populated console state`() {
        val console = BuildConsoleState(
            taskGroups = listOf(
                BuildTaskGroup(
                    module = ":app",
                    tasks = listOf(
                        BuildTaskLine(
                            path = ":app:compileDebugKotlin",
                            name = "compileDebugKotlin",
                            result = BuildEvent.TaskResult.SUCCESS,
                        ),
                        BuildTaskLine(
                            path = ":app:packageDebug",
                            name = "packageDebug",
                        ),
                    ),
                ),
            ),
            logs = listOf(
                BuildLogLine(text = "Compiling", isError = false),
                BuildLogLine(text = "e: failed", isError = true),
            ),
            problems = listOf(
                BuildProblem(
                    severity = BuildEvent.ProblemSeverity.ERROR,
                    message = "Unresolved reference",
                    filePath = "/project/Main.kt",
                    fileName = "Main.kt",
                    line = 12,
                    column = 7,
                ),
                BuildProblem(
                    severity = BuildEvent.ProblemSeverity.WARNING,
                    message = "Deprecated API",
                ),
            ),
        )

        assertEquals(
            """
            Build failed

            2 problems
              [ERROR] Unresolved reference (Main.kt:12:7)
              [WARNING] Deprecated API

            Tasks
            :app
              compileDebugKotlin SUCCESS
              packageDebug

            Output
            Compiling
            e: failed
            """.trimIndent() + "\n",
            console.toClipboardText("Build failed", "2 problems", "Tasks", "Output"),
        )
    }

    @Test
    fun `clipboard text serializes empty console state`() {
        assertEquals(
            "Ready\n",
            BuildConsoleState().toClipboardText("Ready", "0 problems", "Tasks", "Output"),
        )
    }
}
