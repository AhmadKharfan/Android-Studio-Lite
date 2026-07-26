package com.ahmadkharfan.androidstudiolite.data.local

import java.io.File

internal object ProjectLanguageDetector {

    fun detectLanguage(dir: File): String {
        val sources = dir.walkTopDown()
            .onEnter { !LocalFsSupport.isIgnoredDir(it) }
            .filter { it.isFile && it.isMainProjectSource(dir) }
        var sawKotlin = false
        var sawJava = false
        for (file in sources) {
            when (file.extension) {
                "kt" -> sawKotlin = true
                "java" -> sawJava = true
            }
            if (sawKotlin && sawJava) break
        }
        return when {
            sawKotlin && sawJava -> "Java + Kotlin"
            sawKotlin -> "Kotlin"
            sawJava -> "Java"
            usesKotlinPlugin(dir) -> "Kotlin"
            else -> "Java"
        }
    }

    private fun File.isMainProjectSource(projectRoot: File): Boolean {
        val parts = relativeToOrNull(projectRoot)?.invariantSeparatorsPath?.split('/').orEmpty()
        if (parts.firstOrNull() == "buildSrc") return false
        val src = parts.indexOf("src")
        if (src < 0 || src + 2 >= parts.size) return false
        val sourceSet = parts[src + 1]
        val sourceDirectory = parts[src + 2]
        return sourceSet != "test" && sourceSet != "androidTest" &&
            sourceDirectory in setOf("java", "kotlin")
    }

    private fun usesKotlinPlugin(dir: File): Boolean =
        listOf("build.gradle.kts", "build.gradle", "app/build.gradle.kts", "app/build.gradle")
            .asSequence()
            .map { File(dir, it) }
            .filter(File::isFile)
            .any { file ->
                val script = runCatching { file.readText() }.getOrDefault("")
                script.contains("org.jetbrains.kotlin") ||
                    script.contains("kotlin(\"android\")") ||
                    script.contains("kotlin-android")
            }
}
