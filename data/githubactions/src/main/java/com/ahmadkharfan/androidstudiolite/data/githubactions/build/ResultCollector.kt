package com.ahmadkharfan.androidstudiolite.data.githubactions.build

import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubApiClient
import com.ahmadkharfan.androidstudiolite.data.githubactions.api.WorkflowRun
import com.ahmadkharfan.androidstudiolite.data.githubactions.logs.GradleProblemParser
import com.ahmadkharfan.androidstudiolite.data.githubactions.workflow.BuildWorkflow
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent.RemoteBuildPhase
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildTasks
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Turns a finished run's `asl-result` artifact into build events: the task outcomes, the Gradle log,
 * the reason for a failure, and the verified APK (re-signed on the device when [signing] is set) or AAB.
 */
internal class ResultCollector(
    private val api: GitHubApiClient,
    private val downloadDir: File,
    private val signing: ApkSigning?,
) {

    private val annotations = RunAnnotations(api)

    /**
     * Emits the run's results and returns whether it produced what [request] asked for. A null [request]
     * (re-attached build, original request unknown) accepts whichever artifact the run produced.
     * Compiler problems in the log are reported against files under [projectRoot].
     */
    suspend fun collect(
        handle: BuildHandle,
        run: WorkflowRun,
        request: BuildRequest?,
        projectRoot: File,
        liveLog: LiveLog?,
        emit: suspend (BuildEvent) -> Unit,
    ): Boolean {
        val artifact = runCatching { api.artifacts(handle.owner, handle.repo, run.id) }.getOrNull()
            ?.firstOrNull { it.name == BuildWorkflow.RESULT_ARTIFACT && !it.expired }
        val conclusionProblem = GitHubBuildMessages.forConclusion(run.conclusion, run.htmlUrl)
        if (artifact == null) {
            emit(error(conclusionProblem ?: "The build finished on GitHub but left no results."))
            // Without results, GitHub's own words are the only explanation (e.g. a job that was never started).
            annotations.failures(handle, run.id).forEach { emit(error("GitHub: $it")) }
            return false
        }
        emit(BuildEvent.StatusChanged(RemoteBuildPhase.DOWNLOADING, "Downloading the build results…"))
        val resultDir = File(downloadDir, run.id.toString()).apply { deleteRecursively(); mkdirs() }
        val zip = File(resultDir, "result.zip")
        api.downloadArtifact(handle.owner, handle.repo, artifact.id, zip)
        withContext(Dispatchers.IO) { unzip(zip, File(resultDir, "result")) }
        runCatching { api.deleteArtifact(handle.owner, handle.repo, artifact.id) }
        // GitHub-hosted runners check a repository out at /home/runner/work/<repo>/<repo>.
        val problems = GradleProblemParser(projectRoot, workspace = "/home/runner/work/${handle.repo}/${handle.repo}")
        return report(File(resultDir, "result"), request, conclusionProblem, problems, liveLog, emit)
    }

    private suspend fun report(
        dir: File,
        request: BuildRequest?,
        conclusionProblem: String?,
        problems: GradleProblemParser,
        liveLog: LiveLog?,
        emit: suspend (BuildEvent) -> Unit,
    ): Boolean {
        taskEvents(File(dir, "events.ndjson")).forEach { emit(it) }
        val log = File(dir, "build.log").takeIf(File::isFile)?.readLines().orEmpty()
        emitLog(log, liveLog, emit)
        val manifest = File(dir, "asl-result.json").takeIf(File::isFile)
            ?.let { runCatching { JSON.decodeFromString<ResultManifest>(it.readText()) }.getOrNull() }
        val found = log.mapNotNull(problems::parse).distinct().take(MAX_PROBLEMS)
        if (conclusionProblem != null || manifest?.success != true) {
            val reason = whatWentWrong(log)?.let { "Gradle: $it" }
            emit(error(reason ?: conclusionProblem ?: "The build failed."))
            if (reason != null && conclusionProblem != null) emit(BuildEvent.Problem(BuildEvent.ProblemSeverity.INFO, conclusionProblem))
            found.forEach { emit(it) }
            return false
        }
        found.forEach { emit(it) }
        return emitArtifact(dir, request, manifest, emit)
    }

    /** The log lines not already shown live; all of it, after a marker, if live output had gaps. */
    private suspend fun emitLog(log: List<String>, liveLog: LiveLog?, emit: suspend (BuildEvent) -> Unit) {
        val remaining = when {
            liveLog == null -> log
            liveLog.complete -> log.drop(liveLog.linesShown)
            else -> {
                emit(BuildEvent.Output("── Full build log ──", BuildEvent.OutputStream.STDOUT))
                log
            }
        }
        remaining.takeLast(MAX_LOG_LINES).forEach { emit(BuildEvent.Output(it, BuildEvent.OutputStream.STDOUT)) }
    }

    private suspend fun emitArtifact(
        dir: File,
        request: BuildRequest?,
        manifest: ResultManifest,
        emit: suspend (BuildEvent) -> Unit,
    ): Boolean {
        if (request?.kind == BuildKind.CLEAN || request?.kind == BuildKind.MODEL) return true
        val (kind, entry) = pickArtifact(manifest, request)
        val file = entry?.let { File(File(dir, "artifacts"), it.name) }?.takeIf(File::isFile)
        if (entry == null || file == null) {
            emit(error("The build succeeded but produced no ${kind.name}."))
            return false
        }
        if (file.length() != entry.sizeBytes || !sha256(file).equals(entry.sha256, ignoreCase = true)) {
            emit(error("The downloaded ${entry.name} didn't match what GitHub built. Build again."))
            return false
        }
        val signing = signing
        if (signing == null) {
            emit(BuildEvent.ArtifactProduced(file = file, kind = kind, sizeBytes = entry.sizeBytes, sha256 = entry.sha256))
            return true
        }
        return emitSigned(signing, file, kind, isRelease(request, entry), emit)
    }

    /**
     * The artifact [request] asked for (preferring one named after its variant) and its kind. A null
     * [request] accepts an APK, else an AAB.
     */
    private fun pickArtifact(manifest: ResultManifest, request: BuildRequest?): Pair<BuildEvent.ArtifactKind, ResultArtifact?> {
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
        return kind to entry
    }

    private suspend fun emitSigned(
        signing: ApkSigning,
        built: File,
        kind: BuildEvent.ArtifactKind,
        release: Boolean,
        emit: suspend (BuildEvent) -> Unit,
    ): Boolean {
        val signed = File(File(built.parentFile?.parentFile, "signed"), built.name)
        val bundle = kind == BuildEvent.ArtifactKind.AAB
        val certificate = runCatching {
            if (bundle) signing.signBundle(built, signed, release) else signing.sign(built, signed, release)
        }
        val failure = certificate.exceptionOrNull()
        if (failure is CancellationException) throw failure
        if (failure != null) {
            val what = if (bundle) "App Bundle" else "APK"
            emit(error("Couldn't sign the $what on this device: ${failure.message ?: failure.javaClass.simpleName}"))
            return false
        }
        emit(
            BuildEvent.ArtifactProduced(
                file = signed,
                kind = kind,
                sizeBytes = signed.length(),
                sha256 = sha256(signed),
                signed = true,
                certificateSha256 = certificate.getOrNull(),
            ),
        )
        return true
    }

    /** Release builds are signed with the release keystore; the request knows, else the file name tells. */
    private fun isRelease(request: BuildRequest?, entry: ResultArtifact): Boolean =
        request?.buildType?.equals("release", ignoreCase = true)
            ?: request?.variantName?.takeIf { it.isNotBlank() }?.let(BuildTasks::isReleaseVariant)
            ?: entry.name.contains("release", ignoreCase = true)

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
        const val MAX_PROBLEMS = 200
        const val MAX_REASON_CHARS = 500
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
