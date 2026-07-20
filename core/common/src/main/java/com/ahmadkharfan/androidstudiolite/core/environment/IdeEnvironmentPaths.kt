package com.ahmadkharfan.androidstudiolite.core.environment

import android.content.Context
import java.io.File

object IdeEnvironmentPaths {

    fun root(context: Context): File = context.filesDir

    fun prefix(context: Context): File = File(root(context), "usr").ensureExists()

    fun home(context: Context): File = File(root(context), "home").ensureExists()

    fun projectsDir(context: Context): File =
        File(home(context), "AndroidStudioLiteProjects").ensureExists()

    private fun File.ensureExists(): File = apply { if (!exists()) mkdirs() }
}
