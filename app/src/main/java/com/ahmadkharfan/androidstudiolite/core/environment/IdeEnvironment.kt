package com.ahmadkharfan.androidstudiolite.core.environment

import android.content.Context
import java.io.File

/**
 * Exposes the process environment the on-device terminal (T7/T12) inherits. The on-device build
 * toolchain and its native exec/probe machinery are gone — builds run server-side now — so this is
 * reduced to the environment map the shell picks up (HOME / PREFIX / PATH …). The paths point at the
 * app-private layout in [IdeEnvironmentPaths]; they are harmless when empty.
 */
object IdeEnvironment {

    /** The process environment the terminal shell inherits. */
    fun environment(context: Context): Map<String, String> {
        val prefix = IdeEnvironmentPaths.prefix(context)
        val home = IdeEnvironmentPaths.home(context)
        return buildMap {
            put("PREFIX", prefix.absolutePath)
            put("HOME", home.absolutePath)
            put("TMPDIR", File(prefix, "tmp").absolutePath)
            put("LD_LIBRARY_PATH", listOf(
                File(prefix, "lib").absolutePath,
                context.applicationInfo.nativeLibraryDir,
            ).joinToString(File.pathSeparator))
            // Only the system shell is available until the Linux userland is installed; stale
            // prefix binaries from the old on-device toolchain must not shadow it.
            put("PATH", "/system/bin")
        }
    }
}
