package com.ahmadkharfan.androidstudiolite.tooling.server

import java.io.File
import java.nio.file.Files

/**
 * Generates a throwaway single-module Gradle project the harness can sync and build: a plain
 * `java-library` with one external dependency (to exercise dependency resolution/mapping) and one
 * source file (so there's something to compile). No Android — the desktop host has no SDK — which is
 * exactly the generic-model path the extractor falls back to until the on-device Android build lands.
 */
object SampleProject {

    fun create(): File {
        val dir = Files.createTempDirectory("asl-sample-project").toFile()
        dir.deleteOnExit()

        File(dir, "settings.gradle").writeText(
            """
            rootProject.name = 'sample'
            """.trimIndent(),
        )

        File(dir, "build.gradle").writeText(
            """
            plugins {
                id 'java-library'
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                api 'org.apache.commons:commons-lang3:3.14.0'
            }
            """.trimIndent(),
        )

        val src = File(dir, "src/main/java/com/sample").apply { mkdirs() }
        File(src, "Hello.java").writeText(
            """
            package com.sample;

            import org.apache.commons.lang3.StringUtils;

            public final class Hello {
                public static String greet(String name) {
                    return StringUtils.isBlank(name) ? "Hello, world!" : "Hello, " + name + "!";
                }
            }
            """.trimIndent(),
        )
        return dir
    }
}
