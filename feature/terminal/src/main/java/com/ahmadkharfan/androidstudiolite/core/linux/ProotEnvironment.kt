package com.ahmadkharfan.androidstudiolite.core.linux

import android.content.Context
import android.system.Os
import com.ahmadkharfan.androidstudiolite.core.environment.IdeEnvironment
import com.ahmadkharfan.androidstudiolite.core.environment.IdeEnvironmentPaths
import java.io.File

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

    fun isInstalled(): Boolean =
        File(rootfsDir, "etc/alpine-release").exists() &&
            File(rootfsDir, "bin/busybox").exists()

    fun mapHostPathToGuest(hostPath: String): String = resolveGuestCwd(File(hostPath)).guestCwd

    fun ensureRootfsParent(): File = linuxDir.apply { mkdirs() }

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
            add("-0")
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

            add("-b"); add("/proc/self/fd:/dev/fd")
            add("-b"); add("/proc/self/fd/0:/dev/stdin")
            add("-b"); add("/proc/self/fd/1:/dev/stdout")
            add("-b"); add("/proc/self/fd/2:/dev/stderr")


            add("/usr/bin/env"); add("-i")
            add("HOME=/root")
            add("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
            add("TERM=xterm-256color")
            add("LANG=C.UTF-8")
            add("/bin/sh")
        }
    }

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

            put("HOME", tmpDir.absolutePath)
            put("TERM", "xterm-256color")
            put("PATH", "/system/bin")
        }
    }

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

    fun areBootstrapPackagesInstalled(): Boolean =
        packagesBootstrappedMarker.exists() || File(rootfsDir, "usr/bin/git").exists()

    fun runGuestCommand(script: String): GuestCommandResult {
        if (!isInstalled()) return GuestCommandResult(exitCode = -1, output = "")
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
            val output = process.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
            GuestCommandResult(exitCode = process.waitFor(), output = output)
        }
    }
}

data class GuestCommandResult(val exitCode: Int, val output: String)
