package com.ahmadkharfan.androidstudiolite.core.tooling

import android.util.Log
import com.ahmadkharfan.androidstudiolite.data.gradle.GradleProjectReader
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildSystem
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ProjectModel
import com.ahmadkharfan.androidstudiolite.tooling.proto.BuildParams
import com.ahmadkharfan.androidstudiolite.tooling.proto.BuildResultDto
import com.ahmadkharfan.androidstudiolite.tooling.proto.SyncParams
import com.ahmadkharfan.androidstudiolite.tooling.proto.SyncResult
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/**
 * The full-flavor [BuildSystem]: a client to the on-device Gradle tooling server (a real Gradle/AGP
 * build in a separate process). `sync` asks the server to model the project and maps the result onto
 * the shared [ProjectModel]; if the server is unavailable it falls back to T8's static Gradle parse so
 * the IDE still has a usable (if approximate) model. `build` drives a real Gradle build and streams the
 * server's task/log/problem events onto the shared [BuildEvent] flow.
 */
class GradleToolingBuildSystem(
    private val client: ToolingServerClient,
    private val launcher: ToolingServerLauncher,
    private val staticReader: GradleProjectReader = GradleProjectReader(),
) : BuildSystem {

    private val scope = CoroutineScope(SupervisorJob())

    override suspend fun sync(projectRoot: File): ProjectModel {
        return try {
            client.ensureConnected()
            val response = client.sync(
                SyncParams(
                    projectDir = projectRoot.absolutePath,
                    gradleUserHome = launcher.gradleUserHome(),
                    javaHome = launcher.javaHome(),
                ),
            )
            val error = response.error
            if (error != null) error("tooling server sync failed: ${error.message}")
            ToolingProtoMapper.toProjectModel(SyncResult.fromJson(response.result!!).project)
        } catch (e: Throwable) {
            Log.w(TAG, "Server sync unavailable; falling back to static Gradle parse", e)
            staticReader.read(projectRoot).model
        }
    }

    override fun build(request: BuildRequest): Flow<BuildEvent> = channelFlow {
        val start = System.currentTimeMillis()
        send(BuildEvent.Started(request))
        try {
            client.ensureConnected()
            client.startForeground()

            // Relay streamed server events onto the build flow until the request completes.
            val relay = launch {
                client.events.collect { notification ->
                    ToolingProtoMapper.toBuildEvent(notification)?.let { send(it) }
                }
            }

            val response = client.build(
                BuildParams(
                    projectDir = request.projectRoot.absolutePath,
                    tasks = tasksFor(request),
                    gradleUserHome = launcher.gradleUserHome(),
                    javaHome = launcher.javaHome(),
                ),
            )
            relay.cancel()

            val error = response.error
            if (error != null) {
                send(BuildEvent.Problem(BuildEvent.ProblemSeverity.ERROR, error.message))
                send(BuildEvent.Finished(success = false, durationMillis = System.currentTimeMillis() - start))
            } else {
                val result = BuildResultDto.fromJson(response.result!!)
                send(BuildEvent.Finished(result.success, result.durationMillis))
            }
        } catch (e: Throwable) {
            send(BuildEvent.Problem(BuildEvent.ProblemSeverity.ERROR, e.message ?: e.toString()))
            send(BuildEvent.Finished(success = false, durationMillis = System.currentTimeMillis() - start))
        } finally {
            client.stopForeground()
        }
    }

    override fun cancel() {
        scope.launch { runCatching { client.cancel() } }
    }

    /** Maps a [BuildRequest] to the Gradle task path(s), e.g. `:app:assembleDebug`. */
    private fun tasksFor(request: BuildRequest): List<String> {
        val module = request.modulePath.ifBlank { "" } // ":" root prints as empty prefix
        val variant = request.variantName.replaceFirstChar { it.uppercase() }
        return when (request.kind) {
            BuildKind.ASSEMBLE -> listOf("$module:assemble$variant")
            BuildKind.BUNDLE -> listOf("$module:bundle$variant")
            BuildKind.CLEAN -> listOf("$module:clean")
        }
    }

    private companion object {
        const val TAG = "tooling-server"
    }
}
