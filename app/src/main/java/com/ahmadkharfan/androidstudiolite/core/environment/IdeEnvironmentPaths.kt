package com.ahmadkharfan.androidstudiolite.core.environment

import android.content.Context
import java.io.File

/**
 * On-device storage layout under the app's private files dir (no permission needed — this is not
 * where the user's own project files must live to be shared). Builds run on the remote worker, so
 * there is no JDK/SDK/Gradle toolchain here; what remains is the process prefix the terminal
 * inherits and the root where user projects are stored before being uploaded to the build server.
 */
object IdeEnvironmentPaths {

    fun root(context: Context): File = context.filesDir

    fun prefix(context: Context): File = File(root(context), "usr").ensureExists()

    fun home(context: Context): File = File(root(context), "home").ensureExists()

    /**
     * Where user projects live on device: `<home>/AndroidStudioLiteProjects`. Created, edited, imported
     * and built projects all root here, so a project's id can simply be its directory name.
     */
    fun projectsDir(context: Context): File =
        File(home(context), "AndroidStudioLiteProjects").ensureExists()

    private fun File.ensureExists(): File = apply { if (!exists()) mkdirs() }
}
