package com.ahmadkharfan.androidstudiolite.data.githubactions.build

import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubApiClient
import com.ahmadkharfan.androidstudiolite.data.githubactions.api.WorkflowRun
import com.ahmadkharfan.androidstudiolite.data.githubactions.workflow.BuildWorkflow
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent.RemoteBuildPhase
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Turns a finished run's `asl-result` artifact into build events: the task outcomes, the Gradle log,
 * the reason for a failure, and the verified APK or AAB.
 */
internal class ResultCollector(
    private val api: GitHubApiClient,
    private val downloadDir: File,
) {

    /**
     * Emits the run's results and returns whether it produced what [request] asked for. A null [request]
     * (re-attached build, original request unknown) accepts whichever artifact the run produced.
     */
    suspend fun collect(
        handle: BuildHandle,
        run: WorkflowRun,
        request: BuildRequest?,
        emit: suspend (BuildEvent) -> Unit,
    ): Boolean {
        val artifact = runCatching { api.artifacts(handle.owner, handle.repo, run.id) }.getOrNull()
            ?.firstOrNull { it.name == BuildWorkflow.RESULT_ARTIFACT && !it.expired }
        val conclusionProblem = GitHubBuildMessages.forConclusion(run.conclusion, run.htmlUrl)
        if (artifact == null) {
            emit(error(conclusionProblem ?: "The build finished on GitHub but left no results."))
            return false
        }
        emit(BuildEvent.StatusChanged(RemoteBuildPhase.DOWNLOADING, "Downloading the build results…"))
        val resultDir = File(downloadDir, run.id.toString()).apply { deleteRecursively(); mkdirs() }
        val zip = File(resultDir, "result.zip")
        api.downloadArtifact(handle.owner, handle.repo, artifact.id, zip)
        withContext(Dispatchers.IO) { unzip(zip, File(resultDir, "result")) }
        runCatching { api.deleteArtifact(handle.owner, handle.repo, artifact.id) }
        return report(File(resultDir, "result"), request, conclusionProblem, emit)
    }

    private suspend fun report(
        dir: File,
        request: BuildRequest?,
        conclusionProblem: String?,
        emit: suspend (BuildEvent) -> Unit,
    ): Boolean {
        taskEvents(File(dir, "events.ndjson")).forEach { emit(it) }
        val log = File(dir, "build.log").takeIf(File::isFile)?.readLines().orEmpty()
        log.takeLast(MAX_LOG_LINES).forEach { emit(BuildEvent.Output(it, BuildEvent.OutputStream.STDOUT)) }
        val manifest = File(dir, "asl-result.json").takeIf(File::isFile)
            ?.let { runCatching { JSON.decodeFromString<ResultManifest>(it.readText()) }.getOrNull() }
        if (conclusionProblem != null || manifest?.success != true) {
            val reason = whatWentWrong(log)?.let { "Gradle: $it" }
            emit(error(reason ?: conclusionProblem ?: "The build failed."))
            if (reason != null && conclusionProblem != null) emit(BuildEvent.Problem(BuildEvent.ProblemSeverity.INFO, conclusionProblem))
            return false
        }
        return emitArtifact(dir, request, manifest, emit)
    }

    private suspend fun emitArtifact(
        dir: File,
        request: BuildRequest?,
        manifest: ResultManifest,
        emit: suspend (BuildEvent) -> Unit,
    ): Boolean {
        if (request?.kind == BuildKind.CLEAN || request?.kind == BuildKind.MODEL) return true
        val kinds = when (request?.kind) {
            null -> listOf(BuildEvent.ArtifactKind.APK, BuildEvent.ArtifactKind.AAB)
            BuildKind.BUNDLE -> listOf(BuildEvent.ArtifactKind.AAB)
            else -> listOf(BuildEvent.ArtifactKind.APK)
        }
        val entry = manifest.artifacts.filter { artifact -> kinds.any { it.name == artifact.kind } }
            .sortedWith(
                compareBy<ResultArtifact> { artifact -> kinds.indexOfFirst { it.name == artifact.kind } }
                    .thenByDescending { request != null && it.name.contains(request.variantName, ignoreCase = true) },
            )
            .firstOrNull()
        val kind = entry?.let { found -> kinds.first { it.name == found.kind } } ?: kinds.first()
        val file = entry?.let { File(File(dir, "artifacts"), it.name) }?.takeIf(File::isFile)
        if (entry == null || file == null) {
            emit(error("The build succeeded but produced no ${kind.name}."))
            return false
        }
        if (file.length() != entry.sizeBytes || !sha256(file).equals(entry.sha256, ignoreCase = true)) {
            emit(error("The downloaded ${entry.name} didn't match what GitHub built. Build again."))
            return false
        }
        emit(BuildEvent.ArtifactProduced(file = file, kind = kind, sizeBytes = entry.sizeBytes, sha256 = entry.sha256))
        return true
    }

    private fun taskEvents(file: File): List<BuildEvent> =
        file.takeIf(File::isFile)?.readLines().orEmpty().mapNotNull { line ->
            runCatching { JSON.decodeFromString<TaskLine>(line) }.getOrNull()
                ?.takeIf { it.type == "taskFinished" }
                ?.let { BuildEvent.TaskFinished(it.taskPath, taskResult(it.result)) }
        }

    private fun taskResult(raw: String): BuildEvent.TaskResult =
        BuildEvent.TaskResult.entries.firstOrNull { it.name == raw } ?: BuildEvent.TaskResult.SUCCESS

    /** Gradle's own summary of a failure: the lines under `* What went wrong:`. */
    private fun whatWentWrong(log: List<String>): String? {
        val start = log.indexOfFirst { it.trim() == "* What went wrong:" }
        if (start < 0) return null
        return log.drop(start + 1)
            .takeWhile { it.isNotBlank() && !it.startsWith("* ") }
            .joinToString(" ") { it.trim() }
            .takeIf { it.isNotBlank() }
            ?.take(MAX_REASON_CHARS)
    }

    private fun error(message: String) = BuildEvent.Problem(BuildEvent.ProblemSeverity.ERROR, message)

    private fun unzip(zip: File, into: File) {
        into.mkdirs()
        val root = into.canonicalFile
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            generateSequence { input.nextEntry }.forEach { entry ->
                val target = File(root, entry.name).canonicalFile
                // Refuse entries that would land outside the result folder ("zip slip").
                require(target.path.startsWith(root.path + File.separator)) { "Unsafe path in build result: ${entry.name}" }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { input.copyTo(it) }
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Serializable
    private data class ResultManifest(
        val protocol: Int = 0,
        val success: Boolean = false,
        val artifacts: List<ResultArtifact> = emptyList(),
    )

    @Serializable
    private data class ResultArtifact(val name: String, val kind: String, val sizeBytes: Long, val sha256: String)

    @Serializable
    private data class TaskLine(val type: String, val taskPath: String = "", val result: String = "")

    private companion object {
        const val MAX_LOG_LINES = 5_000
        const val MAX_REASON_CHARS = 500
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
