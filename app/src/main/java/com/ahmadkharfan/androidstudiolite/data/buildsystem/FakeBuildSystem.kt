package com.ahmadkharfan.androidstudiolite.data.buildsystem

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildSystem
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.DependencyModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.DependencyScope
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleType
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ProjectModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.SourceSetModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.VariantModel
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * TEMPORARY, dev-only [BuildSystem]. Emits a scripted, realistic [BuildEvent] stream so the whole
 * build → install → run UI (task tree, streaming logs, structured problems, cancellation, artifacts)
 * is runnable and demonstrable **before** the real backend lands.
 *
 * Builds now run server-side; A2 binds `RemoteBuildSystem` (streaming the same [BuildEvent]s over a
 * WebSocket) in place of this. Until then this stands in so the build console still opens. It performs
 * no real compilation and produces a placeholder artifact path.
 */
class FakeBuildSystem(
    private val stepDelayMillis: Long = DEFAULT_STEP_DELAY_MS,
) : BuildSystem {

    /**
     * When true the next [build] emits a failing script (a compile error + BUILD FAILED) instead of a
     * successful one, then resets. Wired to the editor's "Simulate build failure" dev action.
     */
    @Volatile
    var failNextBuild: Boolean = false

    private val cancelled = AtomicBoolean(false)

    override suspend fun sync(projectRoot: File): ProjectModel {
        delay(stepDelayMillis)
        val appDir = File(projectRoot, "app")
        return ProjectModel(
            name = projectRoot.name.ifBlank { "project" },
            rootDir = projectRoot,
            modules = listOf(
                ModuleModel(
                    path = ":app",
                    name = "app",
                    type = ModuleType.ANDROID_APP,
                    moduleDir = appDir,
                    variants = listOf(
                        VariantModel("debug", buildType = "debug"),
                        VariantModel("release", buildType = "release"),
                    ),
                    sourceSets = listOf(
                        SourceSetModel(
                            name = "main",
                            manifestFile = File(appDir, "src/main/AndroidManifest.xml"),
                        ),
                    ),
                    dependencies = listOf(
                        DependencyModel(
                            coordinate = "androidx.core:core-ktx:1.13.1",
                            scope = DependencyScope.IMPLEMENTATION,
                        ),
                    ),
                ),
            ),
        )
    }

    override fun build(request: BuildRequest): Flow<BuildEvent> {
        val fail = failNextBuild
        failNextBuild = false
        cancelled.set(false)
        return flow {
            val start = System.currentTimeMillis()
            emit(BuildEvent.Started(request))
            step { emit(BuildEvent.Progress("Configuring project '${request.projectRoot.name}'")) }

            val bundle = request.kind == BuildKind.BUNDLE
            val outName = if (bundle) "app-${request.variantName}.aab" else "app-${request.variantName}.apk"
            val outKind = if (bundle) BuildEvent.ArtifactKind.AAB else BuildEvent.ArtifactKind.APK
            val artifact = File(
                request.projectRoot,
                "app/build/outputs/${if (bundle) "bundle" else "apk"}/${request.variantName}/$outName",
            )

            if (fail) {
                emitFailingScript(request)
                emit(BuildEvent.Finished(success = false, durationMillis = elapsed(start)))
            } else {
                emitSucceedingScript(request, bundle = bundle)
                if (guard()) return@flow
                emit(BuildEvent.ArtifactProduced(artifact, outKind))
                emit(BuildEvent.Finished(success = true, durationMillis = elapsed(start)))
            }
        }
    }

    override fun cancel() {
        cancelled.set(true)
    }

    private suspend fun FlowCollector<BuildEvent>.emitSucceedingScript(
        request: BuildRequest,
        bundle: Boolean,
    ) {
        val module = request.modulePath.ifBlank { ":app" }
        val variant = request.variantName.replaceFirstChar { it.uppercase() }
        val packageTask = if (bundle) "bundle$variant" else "package$variant"
        val tasks = listOf(
            "$module:preBuild",
            "$module:merge${variant}Resources",
            "$module:process${variant}Manifest",
            "$module:compile${variant}Kotlin",
            "$module:dexBuilder$variant",
            "$module:$packageTask",
        )
        for (task in tasks) {
            if (guard()) return
            emit(BuildEvent.TaskStarted(task))
            step {
                emit(BuildEvent.Output("> Task $task", BuildEvent.OutputStream.STDOUT))
            }
            if (task.endsWith("Kotlin")) {
                emit(
                    BuildEvent.Problem(
                        severity = BuildEvent.ProblemSeverity.WARNING,
                        message = "variable 'unused' is never used",
                        file = File(request.projectRoot, "app/src/main/java/MainActivity.kt"),
                        line = 24,
                        column = 13,
                    ),
                )
            }
            emit(BuildEvent.TaskFinished(task, BuildEvent.TaskResult.SUCCESS))
        }
    }

    private suspend fun FlowCollector<BuildEvent>.emitFailingScript(request: BuildRequest) {
        val module = request.modulePath.ifBlank { ":app" }
        val variant = request.variantName.replaceFirstChar { it.uppercase() }
        emit(BuildEvent.TaskStarted("$module:preBuild"))
        step { emit(BuildEvent.TaskFinished("$module:preBuild", BuildEvent.TaskResult.SUCCESS)) }

        val compile = "$module:compile${variant}Kotlin"
        emit(BuildEvent.TaskStarted(compile))
        step {
            emit(BuildEvent.Output("> Task $compile FAILED", BuildEvent.OutputStream.STDERR))
        }
        emit(
            BuildEvent.Problem(
                severity = BuildEvent.ProblemSeverity.ERROR,
                message = "unresolved reference: fooBar",
                file = File(request.projectRoot, "app/src/main/java/MainActivity.kt"),
                line = 39,
                column = 11,
            ),
        )
        emit(BuildEvent.TaskFinished(compile, BuildEvent.TaskResult.FAILED))
        step {
            emit(BuildEvent.Output("BUILD FAILED · 1 error", BuildEvent.OutputStream.STDERR))
        }
    }

    /** Runs [block] after a step delay unless cancelled; used between scripted emissions. */
    private suspend inline fun step(block: () -> Unit) {
        delay(stepDelayMillis)
        if (!cancelled.get()) block()
    }

    /** True if the build should stop emitting (cancelled by the user or the coroutine). */
    private suspend fun guard(): Boolean = cancelled.get() || !currentCoroutineContext().isActive

    private fun elapsed(start: Long): Long = (System.currentTimeMillis() - start).coerceAtLeast(1)

    companion object {
        const val DEFAULT_STEP_DELAY_MS = 350L
    }
}
