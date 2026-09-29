package com.ahmadkharfan.androidstudiolite.data.githubactions.workflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildWorkflowTest {

    private val workflow = BuildWorkflow.contents()

    @Test
    fun `shipped workflow carries the version the app expects`() {
        assertEquals(BuildWorkflow.VERSION, BuildWorkflow.versionOf(workflow))
    }

    @Test
    fun `a file without the marker has no version`() {
        assertNull(BuildWorkflow.versionOf("name: Something else\non: push\n"))
    }

    @Test
    fun `workflow declares every input the app dispatches`() {
        val inputs = validInputs().keys
        inputs.forEach { name -> assertTrue("missing input $name", workflow.contains("\n      $name:\n")) }
    }

    @Test
    fun `run name and artifact match what the app looks for`() {
        assertTrue(workflow.contains("run-name: asl-\${{ inputs.correlation_id }}"))
        assertEquals("asl-abc", BuildWorkflow.runName("abc"))
        assertTrue(workflow.contains("name: ${BuildWorkflow.RESULT_ARTIFACT}\n"))
    }

    @Test
    fun `workflow only runs on manual dispatch with read-only contents and its own check runs`() {
        assertTrue(workflow.contains("\non:\n  workflow_dispatch:\n"))
        assertTrue(workflow.contains("\npermissions:\n  contents: read\n  checks: write\n\n"))
    }

    @Test
    fun `a cancelled build leaves no result artifact behind`() {
        listOf("Collect outputs", "Upload result").forEach { step ->
            val block = workflow.substringAfter("- name: $step\n").substringBefore("\n      - name:")
            assertTrue(block, block.lines().any { it == "        if: \"!cancelled()\"" })
        }
    }

    @Test
    fun `inputs never reach a shell through expressions`() {
        val runBlocks = workflow.split("run: |").drop(1).map { it.substringBefore("\n      - name:") }
        runBlocks.forEach { block -> assertFalse("expression inside run: $block", block.contains("\${{")) }
    }

    @Test
    fun `third-party actions are pinned to commits`() {
        val uses = Regex("""uses: (\S+)""").findAll(workflow).map { it.groupValues[1] }.toList()
        assertTrue(uses.isNotEmpty())
        uses.forEach { assertTrue("unpinned action $it", Regex("""@[0-9a-f]{40}$""").containsMatchIn(it)) }
    }

    @Test
    fun `valid request produces the dispatch inputs`() {
        assertEquals(
            mapOf(
                "correlation_id" to "5f0c1e2a-0000",
                "source_ref" to "asl/src/0a1b2c",
                "source_sha" to SHA,
                "tasks" to ":app:assembleDebug :lib:bundleRelease",
                "java_version" to "17",
            ),
            validInputs(),
        )
    }

    @Test
    fun `task validation matches the workflow rules`() {
        listOf(":app:assembleDebug", "assembleDebug", ":feature:home:assemble-demo", ":a_b:c").forEach {
            assertTrue(it, BuildWorkflow.isValidTask(it))
        }
        listOf("", ":", "::app", ":app:assembleDebug;id", "\$(id)", "a b", ":app:", "-Pfoo=bar").forEach {
            assertFalse(it, BuildWorkflow.isValidTask(it))
        }
    }

    @Test
    fun `requests the workflow would reject are refused on the device`() {
        val rejected = listOf<() -> Unit>(
            { BuildWorkflow.dispatchInputs("short", REF, SHA, TASKS, 17) },
            { BuildWorkflow.dispatchInputs(CORRELATION, "main", SHA, TASKS, 17) },
            { BuildWorkflow.dispatchInputs(CORRELATION, REF, "abc", TASKS, 17) },
            { BuildWorkflow.dispatchInputs(CORRELATION, REF, SHA, TASKS, 8) },
            { BuildWorkflow.dispatchInputs(CORRELATION, REF, SHA, emptyList(), 17) },
            { BuildWorkflow.dispatchInputs(CORRELATION, REF, SHA, List(9) { "t$it" }, 17) },
            { BuildWorkflow.dispatchInputs(CORRELATION, REF, SHA, listOf(":app:x;id"), 17) },
        )
        rejected.forEachIndexed { index, call ->
            val failed = runCatching(call).exceptionOrNull() is IllegalArgumentException
            assertTrue("request $index was accepted", failed)
        }
    }

    private fun validInputs() = BuildWorkflow.dispatchInputs(CORRELATION, REF, SHA, TASKS, 17)

    private companion object {
        const val CORRELATION = "5f0c1e2a-0000"
        const val REF = "asl/src/0a1b2c"
        const val SHA = "0123456789abcdef0123456789abcdef01234567"
        val TASKS = listOf(":app:assembleDebug", ":lib:bundleRelease")
    }
}
