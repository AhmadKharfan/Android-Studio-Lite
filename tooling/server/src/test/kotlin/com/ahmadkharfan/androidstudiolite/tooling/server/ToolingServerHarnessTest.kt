package com.ahmadkharfan.androidstudiolite.tooling.server

import com.ahmadkharfan.androidstudiolite.tooling.proto.BuildParams
import com.ahmadkharfan.androidstudiolite.tooling.proto.BuildResultDto
import com.ahmadkharfan.androidstudiolite.tooling.proto.JsonValue
import com.ahmadkharfan.androidstudiolite.tooling.proto.SyncParams
import com.ahmadkharfan.androidstudiolite.tooling.proto.SyncResult
import com.ahmadkharfan.androidstudiolite.tooling.proto.ToolingProto
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Desktop-JVM end-to-end harness for the tooling server: spawns the real fat jar as a subprocess,
 * points the Tooling API at a real Gradle install on this host, and drives a generated sample project
 * through sync + build over the JSON-RPC protocol — the on-device path minus the rebuilt JDK, which
 * isn't hosted yet. Also checks that the server survives an abrupt client disconnect.
 *
 * Both the server jar (`asl.tooling.server.jar`) and a Gradle install (`asl.test.gradle.home`) are
 * supplied by the module's `test` task; if either is missing the tests skip rather than fail.
 */
class ToolingServerHarnessTest {

    private lateinit var serverJar: File
    private var gradleHome: File? = null

    @Before
    fun setUp() {
        val jar = System.getProperty("asl.tooling.server.jar")
        assumeTrue("server fat jar not provided", jar != null && File(jar).isFile)
        serverJar = File(jar)
        gradleHome = System.getProperty("asl.test.gradle.home")?.let { File(it) }?.takeIf { it.isDirectory }
        assumeTrue("no local Gradle install for the harness", gradleHome != null)
    }

    @Test
    fun syncsAndBuildsSampleProjectOverJsonRpc() {
        val project = SampleProject.create()
        JsonRpcTestClient.spawn(serverJar).use { client ->
            // ---- sync ----
            val syncResponse = client.call(ToolingProto.Method.SYNC, syncParams(project).toJson())
            assertNull("sync should not error: ${syncResponse.error}", syncResponse.error)
            val model = SyncResult.fromJson(syncResponse.result!!).project

            assertEquals("sample", model.name)
            val module = model.modules.single { it.path == ":" }
            assertEquals("JVM", module.type)
            assertTrue("expected a main source set", module.sourceSets.any { it.name == "main" })
            assertTrue(
                "expected the commons-lang3 dependency, got ${module.dependencies.map { it.coordinate }}",
                module.dependencies.any { it.coordinate.contains("commons-lang3") },
            )

            // ---- build ----
            val buildResponse = client.call(
                ToolingProto.Method.BUILD,
                BuildParams(
                    projectDir = project.absolutePath,
                    tasks = listOf("clean", "assemble"),
                    gradleInstallation = gradleHome!!.absolutePath,
                    javaHome = System.getProperty("java.home"),
                ).toJson(),
            )
            assertNull("build should not error: ${buildResponse.error}", buildResponse.error)
            val buildResult = BuildResultDto.fromJson(buildResponse.result!!)
            assertTrue("build should succeed", buildResult.success)

            // The build must have streamed task events back over the protocol.
            val taskEvents = client.events.filter { it.type == ToolingProto.EventType.TASK_FINISHED }
            assertTrue("expected task-finished events", taskEvents.isNotEmpty())
            assertTrue(
                "expected :jar to run",
                taskEvents.any { it.params.string("path") == ":jar" },
            )
            assertTrue("expected the produced jar on disk", File(project, "build/libs/sample.jar").isFile)

            // ---- cancel round-trip (nothing running) ----
            val cancel = client.call(ToolingProto.Method.CANCEL, JsonValue.obj())
            assertEquals(false, cancel.result!!.bool("cancelled"))

            // ---- graceful shutdown ----
            val shutdown = client.call(ToolingProto.Method.SHUTDOWN, JsonValue.obj())
            assertNull(shutdown.error)
            assertTrue("server should exit after shutdown", client.waitForExit(30))
        }
    }

    @Test
    fun survivesAbruptClientDisconnect() {
        val project = SampleProject.create()
        val client = JsonRpcTestClient.spawn(serverJar)
        // Establish a live Gradle connection (and daemon) first.
        val syncResponse = client.call(ToolingProto.Method.SYNC, syncParams(project).toJson())
        assertNotNull(syncResponse.result)

        // Slam the pipe shut with no shutdown request — the read loop hits EOF and must clean up + exit.
        client.disconnectAbruptly()
        assertTrue("server must exit on client disconnect, not hang", client.waitForExit(45))
    }

    private fun syncParams(project: File) = SyncParams(
        projectDir = project.absolutePath,
        gradleInstallation = gradleHome!!.absolutePath,
        javaHome = System.getProperty("java.home"),
    )
}
