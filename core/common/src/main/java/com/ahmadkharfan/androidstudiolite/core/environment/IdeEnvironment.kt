package com.ahmadkharfan.androidstudiolite.core.environment

import android.content.Context
import java.io.File

object IdeEnvironment {

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


            put("PATH", "/system/bin")
        }
    }
}
