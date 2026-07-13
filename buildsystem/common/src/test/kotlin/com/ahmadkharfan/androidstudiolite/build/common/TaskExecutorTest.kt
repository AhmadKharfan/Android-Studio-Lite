package com.ahmadkharfan.androidstudiolite.build.common

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskExecutorTest {

    private lateinit var dir: File
    private lateinit var store: FingerprintStore
    private lateinit var recorder: RecordingReporter

    @Before fun setup() {
        dir = Files.createTempDirectory("exec").toFile()
        store = FingerprintStore.load(File(dir, "fp.txt"))
        recorder = RecordingReporter()
    }

    private fun executor(cancellation: CancellationToken = CancellationToken()) =
        TaskExecutor(store, recorder, cancellation)

    /** A task that writes [content] to [output] and counts its runs. */
    private class WriteTask(
        override val path: String,
        private val input: File,
        private val output: File,
        private val content: () -> String,
    ) : BuildTask {
        var runs = 0
        override val inputFiles get() = listOf(input)
        override val outputFiles get() = listOf(output)
        override fun run(context: TaskContext) {
            runs++
            output.writeText(content())
        }
    }

    @Test fun `task runs then is up-to-date on rerun`() {
        val input = File(dir, "in.txt").apply { writeText("x") }
        val out = File(dir, "out.txt")
        val task = WriteTask(":m:t", input, out) { "out" }

        executor().run(listOf(task))
        assertEquals(1, task.runs)
        assertEquals(TaskOutcome.SUCCESS, recorder.lastOutcome(":m:t"))

        executor().run(listOf(task))
        assertEquals(1, task.runs) // not re-run
        assertEquals(TaskOutcome.UP_TO_DATE, recorder.lastOutcome(":m:t"))
    }

    @Test fun `changed input forces a rerun`() {
        val input = File(dir, "in.txt").apply { writeText("x") }
        val out = File(dir, "out.txt")
        val task = WriteTask(":m:t", input, out) { input.readText() }

        executor().run(listOf(task))
        input.writeText("y")
        executor().run(listOf(task))
        assertEquals(2, task.runs)
    }

    @Test fun `deleted output forces a rerun even if fingerprint matches`() {
        val input = File(dir, "in.txt").apply { writeText("x") }
        val out = File(dir, "out.txt")
        val task = WriteTask(":m:t", input, out) { "out" }

        executor().run(listOf(task))
        out.delete()
        executor().run(listOf(task))
        assertEquals(2, task.runs)
    }

    @Test fun `failure clears the stored fingerprint and rethrows`() {
        val input = File(dir, "in.txt").apply { writeText("x") }
        val failing = object : BuildTask {
            override val path = ":m:boom"
            override val inputFiles = listOf(input)
            override val outputFiles = emptyList<File>()
            override fun run(context: TaskContext) = throw BuildFailedException("boom")
        }
        assertThrows(BuildFailedException::class.java) { executor().run(listOf(failing)) }
        assertEquals(TaskOutcome.FAILED, recorder.lastOutcome(":m:boom"))
        assertFalse(File(dir, "fp.txt").readText().contains(":m:boom"))
    }

    @Test fun `cancellation aborts before running the next task`() {
        val cancellation = CancellationToken()
        val input = File(dir, "in.txt").apply { writeText("x") }
        val ran = mutableListOf<String>()
        val t1 = object : BuildTask {
            override val path = ":m:a"
            override val inputFiles = listOf(input)
            override val outputFiles = emptyList<File>()
            override fun run(context: TaskContext) { ran += path; cancellation.cancel() }
        }
        val t2 = object : BuildTask {
            override val path = ":m:b"
            override val inputFiles = listOf(input)
            override val outputFiles = emptyList<File>()
            override fun run(context: TaskContext) { ran += path }
        }
        assertThrows(BuildCancelledException::class.java) { executor(cancellation).run(listOf(t1, t2)) }
        assertTrue(ran.contains(":m:a"))
        assertFalse(ran.contains(":m:b"))
    }

    private class RecordingReporter : BuildReporter {
        private val outcomes = LinkedHashMap<String, TaskOutcome>()
        override fun taskFinished(taskPath: String, outcome: TaskOutcome) { outcomes[taskPath] = outcome }
        fun lastOutcome(path: String) = outcomes[path]
    }
}
