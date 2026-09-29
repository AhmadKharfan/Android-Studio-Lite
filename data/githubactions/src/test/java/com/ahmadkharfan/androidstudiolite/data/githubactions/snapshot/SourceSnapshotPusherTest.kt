package com.ahmadkharfan.androidstudiolite.data.githubactions.snapshot

import java.io.File
import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SourceSnapshotPusherTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var project: File
    private lateinit var remote: File
    private var now = 1_700_000_000_000L
    private val pusher by lazy { SourceSnapshotPusher(File(tmp.root, "cache"), nowMillis = { now }) }

    @Before
    fun setUp() {
        project = tmp.newFolder("MyApp")
        remote = File(tmp.root, "remote.git")
        Git.init().setBare(true).setDirectory(remote).call().close()

        write("settings.gradle.kts", "include(\":app\")")
        write("build.gradle.kts", "")
        write("gradlew", "#!/bin/sh\necho gradle")
        write("gradle/wrapper/gradle-wrapper.properties", "distributionUrl=x")
        write("scripts/setup", "#!/usr/bin/env bash\necho hi")
        write("app/build.gradle.kts", "plugins {}")
        write("app/src/main/AndroidManifest.xml", "<manifest/>")
        write("local.properties", "sdk.dir=/data/sdk\nMAPS_KEY=secret")
        write("app/build/outputs/apk/debug/app-debug.apk", "stale")
        write("build/tmp/x", "stale")
        write(".gradle/8.0/file", "cache")
        write(".idea/workspace.xml", "<x/>")
        write(".git/HEAD", "ref: refs/heads/main")
        write(".kotlin/sessions/x", "s")
        // A module that happens to be called "build" is source, not output.
        write("tools/build/build.gradle.kts", "")
        write("tools/build/src/main/kotlin/A.kt", "class A")
        write("tools/settings.gradle.kts", "")
    }

    private fun write(path: String, text: String) {
        File(project, path).apply { parentFile.mkdirs() }.writeText(text)
    }

    private suspend fun push() = pusher.push(project, remote.toURI().toString(), "gho_test", "MyApp")

    private fun <T> withRemote(block: (Repository) -> T): T = Git.open(remote).use { block(it.repository) }

    private fun files(sha: String): Map<String, FileMode> = withRemote { repo ->
        RevWalk(repo).use { walk ->
            val tree = walk.parseCommit(ObjectId.fromString(sha)).tree
            TreeWalk(repo).use { treeWalk ->
                treeWalk.addTree(tree)
                treeWalk.isRecursive = true
                buildMap { while (treeWalk.next()) put(treeWalk.pathString, treeWalk.getFileMode(0)) }
            }
        }
    }

    @Test
    fun `snapshot lands on the project's branch in the build repository`() = runTest {
        val snapshot = push()

        assertEquals("asl/src/${pusher.projectKey(project)}", snapshot.branch)
        val remoteHead = withRemote { it.exactRef("refs/heads/${snapshot.branch}")?.objectId?.name }
        assertEquals(snapshot.sha, remoteHead)
    }

    @Test
    fun `sources are included and tool output, caches and machine files are not`() = runTest {
        val paths = files(push().sha).keys

        assertEquals(
            setOf(
                "settings.gradle.kts",
                "build.gradle.kts",
                "gradlew",
                "gradle/wrapper/gradle-wrapper.properties",
                "scripts/setup",
                "app/build.gradle.kts",
                "app/src/main/AndroidManifest.xml",
                "tools/build/build.gradle.kts",
                "tools/build/src/main/kotlin/A.kt",
                "tools/settings.gradle.kts",
            ),
            paths,
        )
    }

    @Test
    fun `gradle wrapper is executable in the snapshot`() = runTest {
        val modes = files(push().sha)

        assertEquals(FileMode.EXECUTABLE_FILE, modes["gradlew"])
        assertEquals(FileMode.EXECUTABLE_FILE, modes["scripts/setup"])
        assertEquals(FileMode.REGULAR_FILE, modes["build.gradle.kts"])
    }

    @Test
    fun `snapshots never accumulate history`() = runTest {
        push()
        write("app/src/main/Changed.kt", "class Changed")
        now += 60_000

        val second = push()

        val parents = withRemote { repo -> RevWalk(repo).use { it.parseCommit(ObjectId.fromString(second.sha)).parentCount } }
        assertEquals(0, parents)
        assertTrue("app/src/main/Changed.kt" in files(second.sha))
    }

    @Test
    fun `unchanged project produces the same tree`() = runTest {
        val first = push()
        now += 60_000
        val second = push()

        val trees = withRemote { repo ->
            RevWalk(repo).use { walk ->
                listOf(first.sha, second.sha).map { walk.parseCommit(ObjectId.fromString(it)).tree.id }
            }
        }
        assertEquals(trees[0], trees[1])
        assertNotEquals(first.sha, second.sha)
    }

    @Test
    fun `the project's own git repository is left alone`() = runTest {
        val head = File(project, ".git/HEAD").readText()

        push()

        assertEquals(head, File(project, ".git/HEAD").readText())
        assertFalse(File(project, ".git/refs/heads/asl").exists())
    }

    @Test
    fun `different projects get different keys and one project keeps its key`() {
        val other = tmp.newFolder("Other")

        assertNotEquals(pusher.projectKey(project), pusher.projectKey(other))
        assertEquals(pusher.projectKey(project), pusher.projectKey(File(project.path)))
        assertTrue(Regex("^[0-9a-f]{32}$").matches(pusher.projectKey(project)))
    }

    @Test
    fun `files too large for github fail the snapshot with the offending path`() = runTest {
        val strict = SourceSnapshotPusher(File(tmp.root, "cache2"), maxFileBytes = 100)
        write("app/src/main/assets/big.bin", "x".repeat(200))

        val error = runCatching { strict.push(project, remote.toURI().toString(), "t", "MyApp") }.exceptionOrNull()

        assertTrue(error is SnapshotException)
        assertTrue(error!!.message!!, error.message!!.contains("app/src/main/assets/big.bin"))
    }

    @Test
    fun `an unreachable remote is reported as a snapshot failure`() = runTest {
        val missing = File(tmp.root, "missing.git").toURI().toString()

        val error = runCatching { pusher.push(project, missing, "t", "MyApp") }.exceptionOrNull()

        assertTrue(error.toString(), error is SnapshotException)
    }

    @Test
    fun `an empty project is refused`() = runTest {
        val empty = tmp.newFolder("Empty")

        val error = runCatching { pusher.push(empty, remote.toURI().toString(), "t", "Empty") }.exceptionOrNull()

        assertTrue(error is SnapshotException)
    }
}
