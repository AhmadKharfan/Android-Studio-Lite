package com.ahmadkharfan.androidstudiolite.core.linux

import android.content.Context
import android.system.Os
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Progress of downloading + extracting the Linux userland. */
sealed interface LinuxInstallState {
    /** The device architecture has no published rootfs; the full userland is unavailable. */
    data object Unsupported : LinuxInstallState
    data object NotInstalled : LinuxInstallState
    data class Downloading(val progress: Float) : LinuxInstallState
    data object Extracting : LinuxInstallState
    data object BootstrappingPackages : LinuxInstallState
    data object Installed : LinuxInstallState
    data class Failed(val message: String) : LinuxInstallState
}

/**
 * Downloads the [LinuxDistro] rootfs on demand and extracts it into app-private storage, keeping the
 * APK small (nothing is bundled). Extraction goes to a staging directory that is atomically renamed
 * into place, so a crash mid-install never leaves a half-populated rootfs that looks "installed".
 */
class LinuxBootstrapInstaller(
    private val context: Context,
    private val proot: ProotEnvironment,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<LinuxInstallState> = _state.asStateFlow()

    private fun initialState(): LinuxInstallState = diskState()

    /** Re-read the on-disk rootfs so UI state survives process restarts and partial failures. */
    fun refreshState() {
        val disk = diskState()
        val current = _state.value
        if (current is LinuxInstallState.Downloading ||
            current is LinuxInstallState.Extracting ||
            current is LinuxInstallState.BootstrappingPackages
        ) return
        // Installed on disk always wins over Failed/NotInstalled in memory.
        _state.value = disk
    }

    /** Remove the installed userland so [install] can run again. */
    fun uninstall() {
        proot.rootfsDir.deleteRecursively()
        _state.value = diskState()
    }

    private fun diskState(): LinuxInstallState = when {
        proot.isInstalled() -> LinuxInstallState.Installed
        !LinuxDistro.isSupportedDevice() -> LinuxInstallState.Unsupported
        else -> LinuxInstallState.NotInstalled
    }

    /** Idempotent: a no-op if already installed or currently installing. */
    suspend fun install() {
        if (proot.isInstalled()) {
            ensureBootstrapPackages()
            _state.value = LinuxInstallState.Installed
            return
        }
        when (_state.value) {
            is LinuxInstallState.Downloading,
            is LinuxInstallState.Extracting,
            is LinuxInstallState.BootstrappingPackages,
            -> return
            else -> Unit
        }
        val rootfs = LinuxDistro.forThisDevice() ?: run {
            _state.value = LinuxInstallState.Unsupported
            return
        }
        withContext(ioDispatcher) {
            val parent = proot.ensureRootfsParent()
            val archive = File(parent, "rootfs-download.tar.xz")
            val staging = File(parent, "alpine-staging")
            try {
                _state.value = LinuxInstallState.Downloading(0f)
                downloadWithChecksum(rootfs.url, rootfs.sha256, archive)

                _state.value = LinuxInstallState.Extracting
                staging.deleteRecursively()
                staging.mkdirs()
                extract(archive, staging)

                proot.rootfsDir.deleteRecursively()
                if (!staging.renameTo(proot.rootfsDir)) {
                    throw IllegalStateException("could not move rootfs into place")
                }
                proot.prepareRuntime()
                File(proot.ensureRootfsParent(), ".linux-installed").writeText("ok")
                bootstrapPackages()
                _state.value = LinuxInstallState.Installed
            } catch (t: Throwable) {
                staging.deleteRecursively()
                _state.value = LinuxInstallState.Failed(t.message ?: "install failed")
            } finally {
                archive.delete()
            }
        }
    }

    /** Installs git, python3, curl, etc. into the guest — downloaded at runtime, not bundled in the APK. */
    suspend fun ensureBootstrapPackages() {
        if (!proot.isInstalled() || proot.areBootstrapPackagesInstalled()) return
        withContext(ioDispatcher) { bootstrapPackages() }
    }

    private fun bootstrapPackages() {
        _state.value = LinuxInstallState.BootstrappingPackages
        val exit = proot.runGuestCommand(LinuxBootstrapPackages.apkInstallScript())
        if (exit != 0) throw IllegalStateException("tool bootstrap failed (exit $exit)")
        proot.packagesBootstrappedMarker.writeText("ok")
    }

    private fun downloadWithChecksum(url: String, expectedSha256: String, dest: File) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("download failed: HTTP ${response.code}")
            val body = response.body ?: throw IllegalStateException("empty response body")
            val total = body.contentLength()
            val digest = MessageDigest.getInstance("SHA-256")
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var downloaded = 0L
                    while (input.read(buffer).also { read = it } >= 0) {
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            _state.value = LinuxInstallState.Downloading((downloaded.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                throw IllegalStateException("checksum mismatch")
            }
        }
    }

    /** Extract the xz-tar, stripping the leading `alpine-<arch>/` path component. */
    private fun extract(archive: File, into: File) {
        val canonicalInto = into.canonicalPath
        TarArchiveInputStream(XZCompressorInputStream(archive.inputStream().buffered())).use { tar ->
            var entry: TarArchiveEntry? = tar.nextTarEntry
            while (entry != null) {
                val relative = stripLeadingComponent(entry.name)
                if (relative.isEmpty()) {
                    entry = tar.nextTarEntry
                    continue
                }
                val out = File(into, relative)
                // Guard against path traversal from a malformed archive.
                if (!out.canonicalPath.startsWith(canonicalInto + File.separator) && out.canonicalPath != canonicalInto) {
                    throw IllegalStateException("unsafe path in archive: ${entry.name}")
                }
                when {
                    entry.isDirectory -> out.mkdirs()
                    entry.isSymbolicLink -> {
                        out.parentFile?.mkdirs()
                        if (out.exists()) out.delete()
                        Os.symlink(entry.linkName, out.absolutePath)
                    }
                    entry.isLink -> { // hard link -> materialize the target's bytes
                        out.parentFile?.mkdirs()
                        val linkTarget = File(into, stripLeadingComponent(entry.linkName))
                        if (linkTarget.exists()) linkTarget.copyTo(out, overwrite = true)
                    }
                    else -> {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { tar.copyTo(it) }
                        applyMode(out, entry.mode, directory = false)
                    }
                }
                entry = tar.nextTarEntry
            }
        }
    }

    private fun applyMode(file: File, mode: Int, directory: Boolean) {
        val perm = (mode and 0x1FF).let { if (it == 0) (if (directory) 0x1ED else 0x1A4) else it }
        runCatching { Os.chmod(file.absolutePath, perm) }
    }

    private fun stripLeadingComponent(name: String): String {
        val normalized = name.trimStart('/')
        val slash = normalized.indexOf('/')
        return if (slash < 0) "" else normalized.substring(slash + 1)
    }
}
