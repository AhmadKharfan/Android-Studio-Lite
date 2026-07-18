package com.ahmadkharfan.androidstudiolite.core.linux

import android.content.Context
import android.system.Os
import com.ahmadkharfan.androidstudiolite.core.environment.IdeEnvironment
import com.ahmadkharfan.androidstudiolite.core.environment.IdeEnvironmentPaths
import java.io.File

/**
 * Runs an interactive Linux userland (see [LinuxDistro]) under **proot** so the terminal can execute
 * arbitrary tools (apk/apt, python, git, compilers) that Android's bare `/system/bin/sh` can't.
 *
 * ### Why proot, and why it works on a modern targetSdk
 * Android 10+ forbids executing binaries stored in app-private data (`W^X`). proot side-steps this:
 * the `proot` binary itself ships as a native library (`libproot.so`) in the APK, so it lives in the
 * read-only, exec-allowed `nativeLibraryDir`; it then runs every rootfs binary via `ptrace`
 * virtualization rather than a raw `execve` of an app-data file. That means we keep `targetSdk 35`
 * with no `LD_PRELOAD` shim and no lowering of the SDK level.
 *
 * The proot binary NEEDs `libtalloc.so.2` and `libandroid-shmem.so`. Both ship in `nativeLibraryDir`,
 * but Android only extracts files named `lib*.so`, so `libtalloc.so.2` is shipped as `libtalloc.so`
 * and this class recreates the `libtalloc.so.2` SONAME as a symlink in a writable dir on
 * `LD_LIBRARY_PATH`.
 *
 * ### Caveats (validated on an Android 14 x86_64 image: proot 5.1.0 execs, `apk add git` succeeds)
 * - proot relies on `ptrace` and `/proc`; a few heavily locked-down OEM kernels restrict these, in
 *   which case launch fails and we keep running the system shell (see [shellCommand]'s fallback).
 * - ptrace virtualization adds overhead, so guest programs run slower than native — fine for shells,
 *   builds and scripting, not for heavy compute.
 * - Android 15's 16 KB page requirement is satisfied because the shipped proot is 16 KB-aligned and
 *   the downloaded rootfs binaries are 64 KB-aligned; we never rewrite ELF headers (bionic rejects
 *   patched ones).
 */
class ProotEnvironment(private val context: Context) {

    private val nativeLibDir: File get() = File(context.applicationInfo.nativeLibraryDir)

    private val linuxDir: File get() = File(IdeEnvironmentPaths.root(context), "linux")
    val rootfsDir: File get() = File(linuxDir, "alpine")
    private val tmpDir: File get() = File(linuxDir, "tmp")
    private val runtimeLibDir: File get() = File(linuxDir, "runtime-lib")
    private val guestResolvConf: File get() = File(linuxDir, "resolv.conf")

    private val prootBinary: File get() = File(nativeLibDir, "libproot.so")
    private val loader: File get() = File(nativeLibDir, "libproot-loader.so")
    private val loader32: File get() = File(nativeLibDir, "libproot-loader32.so")

    /** True once a usable Alpine rootfs has been extracted into app storage. */
    fun isInstalled(): Boolean =
        File(rootfsDir, "etc/alpine-release").exists() &&
            File(rootfsDir, "bin/busybox").exists()

    /** Maps a host project directory to its path inside the proot guest. */
    fun mapHostPathToGuest(hostPath: String): String = resolveGuestCwd(File(hostPath)).guestCwd

    /** The directory a fresh rootfs is extracted into (created lazily by the installer). */
    fun ensureRootfsParent(): File = linuxDir.apply { mkdirs() }

    /**
     * Prepare the writable bits proot needs before launch: the tmp dir, the projects mount point, and
     * the `libtalloc.so.2` SONAME symlink pointing at the shipped `libtalloc.so`.
     */
    fun prepareRuntime() {
        tmpDir.mkdirs()
        runtimeLibDir.mkdirs()
        File(rootfsDir, "root").mkdirs()
        IdeEnvironmentPaths.projectsDir(context)
        ensureGuestDns()
        val link = File(runtimeLibDir, "libtalloc.so.2")
        val target = File(nativeLibDir, "libtalloc.so").absolutePath
        runCatching {
            if (link.exists() || isSymlink(link)) link.delete()
            Os.symlink(target, link.absolutePath)
        }
    }

    /**
     * The command that launches the interactive shell. When the rootfs is installed this is a proot
     * invocation into Alpine; otherwise it falls back to the system shell so the terminal always works.
     *
     * @param hostWorkingDir optional host project directory — opened as cwd (under `/root/projects/…`
     * or a one-off bind at `/root/project` for paths outside the projects root).
     */
    fun shellCommand(hostWorkingDir: File? = null): List<String> {
        if (!isInstalled()) return listOf(systemShell())
        prepareRuntime()
        val rootfs = rootfsDir.absolutePath
        val projects = IdeEnvironmentPaths.projectsDir(context).absolutePath
        val guest = resolveGuestCwd(hostWorkingDir)
        return buildList {
            add(prootBinary.absolutePath)
            add("--kill-on-exit")
            add("--link2symlink")
            add("-0") // present as root inside the guest
            add("-r"); add(rootfs)
            add("-w"); add(guest.guestCwd)
            add("-b"); add("/dev")
            add("-b"); add("/proc")
            add("-b"); add("/sys")
            add("-b"); add("${tmpDir.absolutePath}:/tmp")
            add("-b"); add("$projects:/root/projects")
            guest.extraBind?.let { (host, mount) ->
                add("-b"); add("$host:$mount")
            }
            add("-b"); add("${guestResolvConf.absolutePath}:/etc/resolv.conf")
            if (File("/system/etc/hosts").exists()) {
                add("-b"); add("/system/etc/hosts:/etc/hosts")
            }
            // Standard fd/stdio binds so interactive tools behave.
            add("-b"); add("/proc/self/fd:/dev/fd")
            add("-b"); add("/proc/self/fd/0:/dev/stdin")
            add("-b"); add("/proc/self/fd/1:/dev/stdout")
            add("-b"); add("/proc/self/fd/2:/dev/stderr")
            // Reset the guest environment cleanly so no Android-specific vars (LD_LIBRARY_PATH etc.)
            // leak into the Linux side.
            add("/usr/bin/env"); add("-i")
            add("HOME=/root")
            add("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
            add("TERM=xterm-256color")
            add("LANG=C.UTF-8")
            add("/bin/sh")
        }
    }

    /** The environment for the launched process (proot itself, or the fallback system shell). */
    fun environment(): Map<String, String> {
        if (!isInstalled()) {
            return IdeEnvironment.environment(context) + mapOf("TERM" to "xterm-256color")
        }
        val ldPath = listOf(runtimeLibDir.absolutePath, nativeLibDir.absolutePath)
            .joinToString(File.pathSeparator)
        return buildMap {
            put("PROOT_LOADER", loader.absolutePath)
            if (loader32.exists()) put("PROOT_LOADER_32", loader32.absolutePath)
            put("PROOT_TMP_DIR", tmpDir.absolutePath)
            put("LD_LIBRARY_PATH", ldPath)
            // Keep Android-specific paths out of the proot host env so they cannot leak into the guest.
            put("HOME", tmpDir.absolutePath)
            put("TERM", "xterm-256color")
            put("PATH", "/system/bin")
        }
    }

    /** proot handles its own cwd (`-w`); the fallback shell starts in [hostWorkingDir] or IDE home. */
    fun workingDirectory(hostWorkingDir: String? = null): File? {
        if (isInstalled()) return null
        return hostWorkingDir?.let(::File)?.takeIf { it.isDirectory }
            ?: IdeEnvironmentPaths.home(context)
    }

    private data class GuestCwd(val guestCwd: String, val extraBind: Pair<String, String>?)

    private fun resolveGuestCwd(hostWorkingDir: File?): GuestCwd {
        if (hostWorkingDir == null) return GuestCwd("/root", extraBind = null)
        val canonical = runCatching { hostWorkingDir.canonicalFile }.getOrDefault(hostWorkingDir)
        val projectsRoot = IdeEnvironmentPaths.projectsDir(context).canonicalFile
        if (canonical.path.startsWith(projectsRoot.path)) {
            val relative = canonical.path.removePrefix(projectsRoot.path).trimStart('/')
            val guest = if (relative.isEmpty()) "/root/projects" else "/root/projects/$relative"
            return GuestCwd(guest, extraBind = null)
        }
        return GuestCwd("/root/project", extraBind = canonical.path to "/root/project")
    }

    private fun systemShell(): String = "/system/bin/sh"

    private fun isSymlink(file: File): Boolean = runCatching {
        file.absolutePath != file.canonicalPath
    }.getOrDefault(false)

    /** Guest DNS for apk/curl — Android has no host /etc/resolv.conf to bind directly. */
    private fun ensureGuestDns() {
        val servers = readAndroidNameservers()
        guestResolvConf.writeText(servers.joinToString("\n", postfix = "\n") { "nameserver $it" })
    }

    private fun readAndroidNameservers(): List<String> {
        val fromProps = listOf("net.dns1", "net.dns2", "net.dns3", "net.dns4").mapNotNull { prop ->
            runCatching {
                Runtime.getRuntime().exec(arrayOf("getprop", prop)).inputStream.bufferedReader().use { it.readLine() }
            }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
        }
        return fromProps.ifEmpty { listOf("8.8.8.8", "8.8.4.4") }
    }

    val packagesBootstrappedMarker: File get() = File(linuxDir, ".packages-bootstrapped")

    /** True once the default apk tool set (git, python3, …) has been installed into the guest. */
    fun areBootstrapPackagesInstalled(): Boolean =
        packagesBootstrappedMarker.exists() || File(rootfsDir, "usr/bin/git").exists()

    /** Run a shell script inside the guest userland (non-interactive). Returns the exit code. */
    fun runGuestCommand(script: String): Int {
        if (!isInstalled()) return -1
        prepareRuntime()
        val argv = shellCommand(null).toMutableList().apply {
            add("-c")
            add(script)
        }
        val pb = ProcessBuilder(argv)
        pb.environment().clear()
        environment().forEach { (key, value) -> pb.environment()[key] = value }
        pb.redirectErrorStream(true)
        return pb.start().let { process ->
            process.inputStream.use { it.readBytes() }
            process.waitFor()
        }
    }
}
