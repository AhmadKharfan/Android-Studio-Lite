package com.ahmadkharfan.androidstudiolite.core.environment

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Real on-device layout for the build toolchain, mirroring android-code-studio's `Environment.java`:
 * a small Linux-ish prefix plus a home directory, both under the app's private storage (no permission
 * needed — this is not where project files live). See
 * docs/build-run/06-full-build-production-study.md §2.
 *
 * ```
 * <filesDir>/usr/lib/jvm/java-17-openjdk   ← JAVA_HOME
 * <filesDir>/home/android-sdk              ← ANDROID_HOME / ANDROID_SDK_ROOT
 * <filesDir>/home/.gradle                  ← GRADLE_USER_HOME
 * ```
 */
object IdeEnvironmentPaths {

    /**
     * Architectures the bundled on-device runtime ships binaries for. The full downloadable toolchain
     * (JDK/SDK/Gradle) is arm-first, but the bundled probe is built for x86 too so ASL runs and
     * completes setup on the standard x86_64 emulator.
     */
    private val SUPPORTED_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    fun root(context: Context): File = context.filesDir

    fun prefix(context: Context): File = File(root(context), "usr").ensureExists()

    fun home(context: Context): File = File(root(context), "home").ensureExists()

    fun javaHome(context: Context, jdkMajorVersion: Int = 17): File =
        File(prefix(context), "lib/jvm/java-$jdkMajorVersion-openjdk")

    fun androidSdkHome(context: Context): File = File(home(context), "android-sdk")

    /** Root under which user projects (created, imported, or git-cloned) live. */
    fun projectsHome(context: Context): File = File(home(context), "projects").ensureExists()

    fun gradleUserHome(context: Context): File = File(home(context), ".gradle")

    /**
     * Where user projects live on device: `<home>/AndroidStudioLiteProjects`. Created, edited, imported
     * and built projects all root here, so a project's id can simply be its directory name.
     */
    fun projectsDir(context: Context): File =
        File(home(context), "AndroidStudioLiteProjects").ensureExists()

    /** Scratch space for in-flight downloads/extraction; safe to delete entirely between installs. */
    fun stagingDir(context: Context): File = File(root(context), "environment-staging").ensureExists()

    /** Per-component marker file recording the installed version, written only after a verified extract. */
    fun markerFile(context: Context, componentId: String): File =
        File(home(context), ".androidstudiolite/installed-$componentId.properties")

    /** `null` when the device's ABI has no published toolchain build (unsupported device). */
    fun deviceAbi(): String? = Build.SUPPORTED_ABIS.firstOrNull { it in SUPPORTED_ABIS }

    private fun File.ensureExists(): File = apply { if (!exists()) mkdirs() }
}
