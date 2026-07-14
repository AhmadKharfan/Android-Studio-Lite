package com.ahmadkharfan.androidstudiolite.tooling.server

import com.ahmadkharfan.androidstudiolite.tooling.proto.BuildParams
import com.ahmadkharfan.androidstudiolite.tooling.proto.BuildResultDto
import com.ahmadkharfan.androidstudiolite.tooling.proto.Events
import com.ahmadkharfan.androidstudiolite.tooling.proto.JsonValue
import com.ahmadkharfan.androidstudiolite.tooling.proto.Request
import com.ahmadkharfan.androidstudiolite.tooling.proto.Response
import com.ahmadkharfan.androidstudiolite.tooling.proto.RpcError
import com.ahmadkharfan.androidstudiolite.tooling.proto.SyncParams
import com.ahmadkharfan.androidstudiolite.tooling.proto.SyncResult
import com.ahmadkharfan.androidstudiolite.tooling.proto.ToolingProto
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.Executors
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.CancellationTokenSource
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType

/**
 * Dispatches JSON-RPC requests to real Gradle operations. `sync` and `build` run on a single worker
 * thread (only one Gradle operation at a time), while `cancel` and `shutdown` are handled inline on
 * the caller's (read-loop) thread so they can interrupt an in-flight operation. Every response and
 * every streamed event goes out through [writer].
 */
class ToolingService(private val writer: MessageWriter) {

    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "asl-tooling-worker").apply { isDaemon = true }
    }

    /** The cancellation source of the currently running Gradle operation, if any. */
    @Volatile private var currentToken: CancellationTokenSource? = null

    fun handle(request: Request) {
        when (request.method) {
            ToolingProto.Method.SYNC, ToolingProto.Method.BUILD -> worker.submit { runRequest(request) }
            ToolingProto.Method.CANCEL -> {
                val cancelled = currentToken?.let { it.cancel(); true } ?: false
                writer.respond(Response.ok(request.id, JsonValue.obj("cancelled" to JsonValue.of(cancelled))))
            }
            ToolingProto.Method.SHUTDOWN -> {
                shutdown()
                writer.respond(Response.ok(request.id, JsonValue.obj()))
            }
            else -> writer.respond(
                Response.failed(request.id, RpcError("unknown method: ${request.method}")),
            )
        }
    }

    /** Cleans up Gradle daemons and the worker; safe to call more than once (EOF and explicit shutdown). */
    fun shutdown() {
        runCatching { currentToken?.cancel() }
        worker.shutdownNow()
        // Ask the Tooling API to stop the daemons it started; internal but the documented mechanism.
        runCatching { org.gradle.tooling.internal.consumer.DefaultGradleConnector.close() }
    }

    private fun runRequest(request: Request) {
        val result = runCatching {
            when (request.method) {
                ToolingProto.Method.SYNC -> sync(SyncParams.fromJson(request.params)).toJson()
                ToolingProto.Method.BUILD -> build(BuildParams.fromJson(request.params)).toJson()
                else -> error("unhandled method: ${request.method}")
            }
        }
        result.fold(
            onSuccess = { writer.respond(Response.ok(request.id, it)) },
            onFailure = { writer.respond(Response.failed(request.id, RpcError(it.message ?: it.toString(), stackOf(it)))) },
        )
    }

    // ------------------------------------------------------------------ operations

    private fun sync(params: SyncParams): SyncResult {
        val connector = connector(
            params.projectDir, params.gradleInstallation, params.gradleVersion, params.gradleUserHome,
        )
        try {
            connector.connect().use { connection ->
                val cts = GradleConnector.newCancellationTokenSource()
                currentToken = cts
                try {
                    return GradleModelExtractor(writer::emit).extract(connection, params, cts.token())
                } finally {
                    currentToken = null
                }
            }
        } finally {
            connector.disconnect()
        }
    }

    private fun build(params: BuildParams): BuildResultDto {
        val start = System.currentTimeMillis()
        val connector = connector(
            params.projectDir, params.gradleInstallation, params.gradleVersion, params.gradleUserHome,
        )
        try {
            connector.connect().use { connection ->
                val cts = GradleConnector.newCancellationTokenSource()
                currentToken = cts
                try {
                    return runBuild(connection, params, cts, start)
                } finally {
                    currentToken = null
                }
            }
        } finally {
            connector.disconnect()
        }
    }

    private fun runBuild(
        connection: ProjectConnection,
        params: BuildParams,
        cts: CancellationTokenSource,
        start: Long,
    ): BuildResultDto {
        val launcher = connection.newBuild()
            .forTasks(*params.tasks.filter { it.isNotBlank() }.toTypedArray())
            .withCancellationToken(cts.token())
            .setStandardInput("NoOp".byteInputStream())
            .setStandardOutput(LineForwardingOutputStream("stdout", writer::emit))
            .setStandardError(LineForwardingOutputStream("stderr", writer::emit))
            .addProgressListener(TaskProgressListener(writer::emit), setOf(OperationType.TASK))
        params.javaHome?.let { launcher.setJavaHome(File(it)) }
        if (params.arguments.isNotEmpty()) launcher.addArguments(params.arguments)

        return try {
            launcher.run()
            BuildResultDto(success = true, durationMillis = System.currentTimeMillis() - start)
        } catch (_: BuildCancelledException) {
            BuildResultDto(success = false, durationMillis = System.currentTimeMillis() - start)
        } catch (e: Throwable) {
            writer.emit(Events.problem("ERROR", e.message ?: e.toString(), file = null, line = null, column = null))
            BuildResultDto(success = false, durationMillis = System.currentTimeMillis() - start)
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun connector(
        projectDir: String,
        installation: String?,
        version: String?,
        gradleUserHome: String?,
    ): GradleConnector {
        val connector = GradleConnector.newConnector().forProjectDirectory(File(projectDir))
        when {
            installation != null -> connector.useInstallation(File(installation))
            version != null -> connector.useGradleVersion(version)
            else -> connector.useBuildDistribution() // the project's own wrapper
        }
        gradleUserHome?.let { connector.useGradleUserHomeDir(File(it)) }
        return connector
    }

    private fun stackOf(t: Throwable): String =
        StringWriter().also { t.printStackTrace(PrintWriter(it)) }.toString()
}
