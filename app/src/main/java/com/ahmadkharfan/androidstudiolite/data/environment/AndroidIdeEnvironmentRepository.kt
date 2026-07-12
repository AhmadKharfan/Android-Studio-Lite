package com.ahmadkharfan.androidstudiolite.data.environment

import android.content.Context
import android.system.Os
import com.ahmadkharfan.androidstudiolite.BuildConfig
import com.ahmadkharfan.androidstudiolite.core.environment.IdeEnvironment
import com.ahmadkharfan.androidstudiolite.core.environment.IdeEnvironmentPaths
import com.ahmadkharfan.androidstudiolite.domain.model.IdeEnvironmentComponentState
import com.ahmadkharfan.androidstudiolite.domain.model.IdeEnvironmentComponentStatus
import com.ahmadkharfan.androidstudiolite.domain.model.IdeEnvironmentState
import com.ahmadkharfan.androidstudiolite.domain.repository.IdeEnvironmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.TimeUnit

private const val DOWNLOAD_BUFFER_BYTES = 64 * 1024

/** The always-present, bundled component installed from inside the APK — no network required. */
private const val RUNTIME_COMPONENT_ID = "runtime"

/**
 * Installer for the on-device build toolchain. Two component sources:
 *
 * 1. **Bundled runtime** (always present) — the native env probe shipped in the APK. Installed offline
 *    by laying out the toolchain prefix, linking the binary into it, and **actually executing it** to
 *    prove on-device native execution works. This is the same bundle→extract-to-nativeLibraryDir→exec
 *    mechanism android-code-studio uses for its Termux bootstrap (docs/build-run/06 §2, 07).
 * 2. **Downloadable toolchain** (JDK / Android SDK / Gradle) — fetched from the hosted manifest at
 *    [BuildConfig.IDE_ENVIRONMENT_MANIFEST_URL] (resumable, checksum-verified, extracted). Absent until
 *    the toolchain rebuild (docs/build-run/07) is hosted; when unset, setup still completes on the
 *    bundled runtime alone.
 */
class AndroidIdeEnvironmentRepository(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) : IdeEnvironmentRepository {

    private val _state = MutableStateFlow(IdeEnvironmentState(abi = IdeEnvironmentPaths.deviceAbi()))
    private var cachedManifest: EnvironmentManifest? = null
    private var currentInstallJob: Job? = null
    private val extractor = TarXzExtractor { target, link -> Os.symlink(target, link.absolutePath) }

    override fun observeState(): Flow<IdeEnvironmentState> = _state

    override suspend fun refresh() {
        val abi = IdeEnvironmentPaths.deviceAbi()
        if (abi == null) {
            _state.update { IdeEnvironmentState(abi = null, components = listOf(unsupportedDeviceComponent())) }
            return
        }
        val manifest = cachedManifest ?: fetchManifest(abi)?.also { cachedManifest = it }
        _state.update {
            IdeEnvironmentState(
                abi = abi,
                components = buildList {
                    add(runtimeComponentFromDisk())
                    manifest?.components?.forEach { add(remoteComponentFromDisk(it)) }
                },
            )
        }
    }

    override suspend fun installAll() {
        currentInstallJob = currentCoroutineContext()[Job]
        try {
            val abi = IdeEnvironmentPaths.deviceAbi()
            if (abi == null) {
                _state.update { IdeEnvironmentState(abi = null, components = listOf(unsupportedDeviceComponent())) }
                return
            }

            // Ensure the state reflects the full component set before installing.
            refresh()

            // 1. Bundled runtime — always installed, offline, verified by real execution.
            currentCoroutineContext().ensureActive()
            if (runtimeComponentFromDisk().status != IdeEnvironmentComponentStatus.Installed) {
                installRuntime()
            }

            // 2. Downloadable toolchain components, if a manifest is configured/reachable.
            val manifest = cachedManifest
            if (manifest != null) {
                for (spec in manifest.components) {
                    currentCoroutineContext().ensureActive()
                    if (remoteComponentFromDisk(spec).status == IdeEnvironmentComponentStatus.Installed) continue
                    installRemote(spec)
                }
            }
        } finally {
            currentInstallJob = null
        }
    }

    override fun cancelInstall() {
        currentInstallJob?.cancel()
    }

    // ---- Bundled runtime -----------------------------------------------------------------------

    private suspend fun installRuntime() = withContext(Dispatchers.IO) {
        try {
            updateComponent(RUNTIME_COMPONENT_ID) { it.copy(status = IdeEnvironmentComponentStatus.Extracting) }

            // Lay out the toolchain prefix and link the bundled, exec-safe binary into usr/bin.
            val prefix = IdeEnvironmentPaths.prefix(context)
            File(prefix, "bin").mkdirs()
            File(prefix, "lib").mkdirs()
            File(prefix, "tmp").mkdirs()
            IdeEnvironmentPaths.home(context)
            IdeEnvironmentPaths.gradleUserHome(context).mkdirs()

            val probe = IdeEnvironment.probeBinary(context)
            if (!probe.exists()) {
                updateComponent(RUNTIME_COMPONENT_ID) {
                    it.copy(
                        status = IdeEnvironmentComponentStatus.Failed,
                        errorMessage = "Bundled runtime binary missing from the install (nativeLibraryDir). Reinstall the app.",
                    )
                }
                return@withContext
            }
            val link = File(prefix, "bin/asl-env-probe")
            runCatching { if (link.exists() || isSymlink(link)) link.delete() }
            Os.symlink(probe.absolutePath, link.absolutePath)

            // Actually run the native binary — the real proof that on-device execution works.
            currentCoroutineContext().ensureActive()
            updateComponent(RUNTIME_COMPONENT_ID) { it.copy(status = IdeEnvironmentComponentStatus.Verifying) }
            val result = IdeEnvironment.runProbe(context)
            if (!result.ok || !result.stdout.contains("ASL_ENV_PROBE ok")) {
                updateComponent(RUNTIME_COMPONENT_ID) {
                    it.copy(
                        status = IdeEnvironmentComponentStatus.Failed,
                        errorMessage = "On-device runtime check failed (exit ${result.exitCode})." +
                            (result.stderr.takeIf { s -> s.isNotBlank() }?.let { s -> " $s" } ?: ""),
                    )
                }
                return@withContext
            }

            writeMarker(RUNTIME_COMPONENT_ID, runtimeVersion())
            updateComponent(RUNTIME_COMPONENT_ID) {
                it.copy(status = IdeEnvironmentComponentStatus.Installed, downloadedBytes = it.sizeBytes, errorMessage = null)
            }
        } catch (e: Exception) {
            updateComponent(RUNTIME_COMPONENT_ID) {
                it.copy(status = IdeEnvironmentComponentStatus.Failed, errorMessage = e.message ?: "Failed to set up the on-device runtime.")
            }
        }
    }

    private fun runtimeComponentFromDisk(): IdeEnvironmentComponentState {
        val installed = markerMatches(RUNTIME_COMPONENT_ID, runtimeVersion())
        val size = IdeEnvironment.probeBinary(context).let { if (it.exists()) it.length() else 0L }
        return IdeEnvironmentComponentState(
            id = RUNTIME_COMPONENT_ID,
            displayName = "On-device runtime",
            version = "native · ${IdeEnvironmentPaths.deviceAbi() ?: "unknown"}",
            sizeBytes = size,
            status = if (installed) IdeEnvironmentComponentStatus.Installed else IdeEnvironmentComponentStatus.NotInstalled,
            downloadedBytes = if (installed) size else 0,
        )
    }

    private fun runtimeVersion(): String = "${BuildConfig.VERSION_CODE}-${IdeEnvironmentPaths.deviceAbi()}"

    private fun isSymlink(file: File): Boolean = runCatching {
        file.absolutePath != file.canonicalPath
    }.getOrDefault(false)

    // ---- Downloadable toolchain ----------------------------------------------------------------

    private suspend fun installRemote(spec: EnvironmentComponentSpec) = withContext(Dispatchers.IO) {
        val staging = File(IdeEnvironmentPaths.stagingDir(context), "${spec.id}.download")
        try {
            updateComponent(spec.id) { it.copy(status = IdeEnvironmentComponentStatus.Downloading, downloadedBytes = 0) }
            downloadWithResume(spec, staging) { downloaded ->
                updateComponent(spec.id) { it.copy(status = IdeEnvironmentComponentStatus.Downloading, downloadedBytes = downloaded) }
            }

            currentCoroutineContext().ensureActive()
            updateComponent(spec.id) { it.copy(status = IdeEnvironmentComponentStatus.Verifying) }
            val actualSha256 = sha256(staging)
            if (!actualSha256.equals(spec.sha256, ignoreCase = true)) {
                staging.delete()
                updateComponent(spec.id) {
                    it.copy(status = IdeEnvironmentComponentStatus.Failed, errorMessage = "Checksum mismatch after download — please retry.")
                }
                return@withContext
            }

            currentCoroutineContext().ensureActive()
            updateComponent(spec.id) { it.copy(status = IdeEnvironmentComponentStatus.Extracting) }
            val targetDir = File(IdeEnvironmentPaths.root(context), spec.targetPath)
            extractor.extract(staging, targetDir)
            linkJavaLauncher(spec)
            writeMarker(spec.id, spec.version, spec.sha256)
            staging.delete()

            updateComponent(spec.id) {
                it.copy(status = IdeEnvironmentComponentStatus.Installed, downloadedBytes = spec.sizeBytes, errorMessage = null)
            }
        } catch (e: IOException) {
            updateComponent(spec.id) {
                it.copy(status = IdeEnvironmentComponentStatus.Failed, errorMessage = e.message ?: "Network error while installing ${spec.displayName}.")
            }
        } catch (e: SecurityException) {
            staging.delete()
            updateComponent(spec.id) {
                it.copy(status = IdeEnvironmentComponentStatus.Failed, errorMessage = "Rejected unsafe archive for ${spec.displayName}: ${e.message}")
            }
        }
    }

    /**
     * After the JDK component lands, expose `$PREFIX/bin/java` the way Termux does — as a symlink
     * to the JDK's launcher — mirroring the `asl-env-probe` link above. Hosted archives cannot
     * carry this link themselves because it must point at an absolute on-device path.
     */
    private fun linkJavaLauncher(spec: EnvironmentComponentSpec) {
        if (!spec.targetPath.startsWith("usr/lib/jvm/")) return
        val javaBinary = File(IdeEnvironmentPaths.root(context), "${spec.targetPath}/bin/java")
        if (!javaBinary.exists()) return
        runCatching {
            val bin = File(IdeEnvironmentPaths.prefix(context), "bin").apply { mkdirs() }
            val link = File(bin, "java")
            if (link.exists() || isSymlink(link)) link.delete()
            Os.symlink(javaBinary.absolutePath, link.absolutePath)
        }
    }

    private fun downloadWithResume(spec: EnvironmentComponentSpec, destFile: File, onProgress: (Long) -> Unit) {
        destFile.parentFile?.mkdirs()
        val existingBytes = if (destFile.exists()) destFile.length() else 0L
        val resumable = existingBytes in 1 until spec.sizeBytes
        val request = Request.Builder().url(spec.downloadUrl).apply {
            if (resumable) addHeader("Range", "bytes=$existingBytes-")
        }.build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed (HTTP ${response.code}) for ${spec.displayName}")
            }
            val resumed = resumable && response.code == 206
            val body = response.body ?: throw IOException("Empty response body for ${spec.displayName}")
            FileOutputStream(destFile, resumed).use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    var total = if (resumed) existingBytes else 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        total += read
                        onProgress(total)
                    }
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun remoteComponentFromDisk(spec: EnvironmentComponentSpec): IdeEnvironmentComponentState {
        val installed = markerMatches(spec.id, spec.version)
        return IdeEnvironmentComponentState(
            id = spec.id,
            displayName = spec.displayName,
            version = spec.version,
            sizeBytes = spec.sizeBytes,
            status = if (installed) IdeEnvironmentComponentStatus.Installed else IdeEnvironmentComponentStatus.NotInstalled,
            downloadedBytes = if (installed) spec.sizeBytes else 0,
        )
    }

    // ---- Markers -------------------------------------------------------------------------------

    private fun writeMarker(componentId: String, version: String, sha256: String? = null) {
        val marker = IdeEnvironmentPaths.markerFile(context, componentId)
        marker.parentFile?.mkdirs()
        val tmp = File(marker.parentFile, "${marker.name}.tmp")
        val props = Properties().apply {
            setProperty("version", version)
            sha256?.let { setProperty("sha256", it) }
        }
        tmp.outputStream().use { props.store(it, "AndroidStudioLite installed component marker") }
        if (!tmp.renameTo(marker)) {
            marker.delete()
            tmp.copyTo(marker, overwrite = true)
            tmp.delete()
        }
    }

    private fun markerMatches(componentId: String, version: String): Boolean {
        val marker = IdeEnvironmentPaths.markerFile(context, componentId)
        return marker.exists() && runCatching {
            val props = Properties().apply { marker.inputStream().use { load(it) } }
            props.getProperty("version") == version
        }.getOrDefault(false)
    }

    private fun updateComponent(id: String, transform: (IdeEnvironmentComponentState) -> IdeEnvironmentComponentState) {
        _state.update { current ->
            current.copy(components = current.components.map { if (it.id == id) transform(it) else it })
        }
    }

    private suspend fun fetchManifest(abi: String): EnvironmentManifest? = withContext(Dispatchers.IO) {
        val url = BuildConfig.IDE_ENVIRONMENT_MANIFEST_URL
        if (url.isBlank()) return@withContext null
        try {
            val request = Request.Builder().url(url).build()
            val bodyText = httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
            bodyText?.let { EnvironmentManifest.parse(it, abi) }
        } catch (e: IOException) {
            null
        }
    }

    private fun unsupportedDeviceComponent() = IdeEnvironmentComponentState(
        id = "unsupported-device",
        displayName = "Device architecture",
        version = "-",
        sizeBytes = 0,
        status = IdeEnvironmentComponentStatus.Failed,
        errorMessage = "This device's CPU architecture (${android.os.Build.SUPPORTED_ABIS.firstOrNull()}) is not supported.",
    )

}
