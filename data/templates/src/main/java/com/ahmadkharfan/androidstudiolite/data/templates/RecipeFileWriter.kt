package com.ahmadkharfan.androidstudiolite.data.templates

import java.io.File

internal object RecipeFileWriter {
    fun writeTo(
        projectRoot: File,
        renderedFiles: Map<String, String>,
        wrapperSource: GradleWrapperSource?,
    ) {
        for ((relPath, content) in renderedFiles) {
            val target = File(projectRoot, relPath)
            target.parentFile?.mkdirs()
            target.writeText(content)
        }
        wrapperSource?.let { writeWrapper(projectRoot, it) }
    }

    private fun writeWrapper(projectRoot: File, source: GradleWrapperSource) {
        for (relPath in GradleWrapperSource.PATHS) {
            val target = File(projectRoot, relPath)
            target.parentFile?.mkdirs()
            source.open(relPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        File(projectRoot, GradleWrapperSource.GRADLEW).setExecutable(true, false)
    }
}
