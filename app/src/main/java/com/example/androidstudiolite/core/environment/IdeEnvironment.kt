package com.example.androidstudiolite.core.environment

import android.content.Context
import java.io.File

/**
 * Runs native binaries in the on-device toolchain environment and exposes the environment variables a
 * real build needs (JAVA_HOME / ANDROID_HOME / PREFIX / PATH / LD_LIBRARY_PATH), mirroring
 * android-code-studio's `Environment.java`.
 *
 * The one binary that ships in the APK today is the env probe ([probeBinary]); it lives in
 * nativeLibraryDir, the only app-writable-adjacent location a binary may be exec()'d from on API 29+.
 * The full JDK/SDK/Gradle binaries the toolchain rebuild produces (docs/build-run/07-…) run the same
 * way through [run].
 */
object IdeEnvironment {

    /** The bundled env-probe executable, shipped as lib*.so and extracted to nativeLibraryDir. */
    fun probeBinary(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, "libasl_env_probe.so")

    data class ExecResult(val exitCode: Int, val stdout: String, val stderr: String) {
        val ok: Boolean get() = exitCode == 0
    }

    /** The process environment every build/exec inherits. */
    fun environment(context: Context): Map<String, String> {
        val prefix = IdeEnvironmentPaths.prefix(context)
        val home = IdeEnvironmentPaths.home(context)
        return buildMap {
            put("PREFIX", prefix.absolutePath)
            put("HOME", home.absolutePath)
            put("JAVA_HOME", IdeEnvironmentPaths.javaHome(context).absolutePath)
            put("ANDROID_HOME", IdeEnvironmentPaths.androidSdkHome(context).absolutePath)
            put("ANDROID_SDK_ROOT", IdeEnvironmentPaths.androidSdkHome(context).absolutePath)
            put("GRADLE_USER_HOME", IdeEnvironmentPaths.gradleUserHome(context).absolutePath)
            put("TMPDIR", File(prefix, "tmp").absolutePath)
            // The bundled toolchain binaries carry a RUNPATH; LD_LIBRARY_PATH is consulted before
            // DT_RUNPATH by Bionic, so pointing it at our own lib dir lets relocated binaries resolve
            // their libraries regardless of the path baked in at link time.
            put("LD_LIBRARY_PATH", listOf(
                File(prefix, "lib").absolutePath,
                File(IdeEnvironmentPaths.javaHome(context), "lib").absolutePath,
                context.applicationInfo.nativeLibraryDir,
            ).joinToString(File.pathSeparator))
            put("PATH", listOf(
                File(prefix, "bin").absolutePath,
                File(IdeEnvironmentPaths.javaHome(context), "bin").absolutePath,
            ).joinToString(File.pathSeparator))
        }
    }

    /**
     * Executes [binary] with [args] under the toolchain [environment], captures stdout/stderr, and
     * returns the result. Runs synchronously — call off the main thread.
     */
    fun run(
        context: Context,
        binary: File,
        args: List<String> = emptyList(),
        workingDir: File = IdeEnvironmentPaths.home(context),
    ): ExecResult {
        val process = ProcessBuilder(listOf(binary.absolutePath) + args)
            .directory(workingDir.takeIf { it.exists() })
            .apply { environment().putAll(environment(context)) }
            .start()

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exit = process.waitFor()
        return ExecResult(exit, stdout.trim(), stderr.trim())
    }

    /** Runs the env probe and returns its result — the on-device "can we execute native binaries?" check. */
    fun runProbe(context: Context): ExecResult = run(context, probeBinary(context))
}
