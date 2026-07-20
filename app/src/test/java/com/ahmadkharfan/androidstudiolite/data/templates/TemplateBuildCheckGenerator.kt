package com.ahmadkharfan.androidstudiolite.data.templates

import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.ProjectBuildDsl
import com.ahmadkharfan.androidstudiolite.domain.model.TemplateLanguage
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class TemplateBuildCheckGenerator {

    @Test
    fun `generate every template into the requested directory`() {
        val out = System.getProperty(OUT_PROPERTY)
        assumeTrue("set -D$OUT_PROPERTY=<dir> to generate", out != null)

        val outDir = File(out!!).apply { mkdirs() }
        val assets = File("src/main/assets/wrapper")
        check(assets.isDirectory) { "wrapper assets not found at ${assets.absolutePath}" }
        val engine = ProjectTemplateEngine(
            wrapperSource = GradleWrapperSource { path -> File(assets, path).inputStream() },
        )

        for (template in TemplateRegistry.DEFAULT) {
            val languages = buildList {
                add(TemplateLanguage.KOTLIN)
                if (template.supportsJava) add(TemplateLanguage.JAVA)
            }
            for (language in languages) {
                val id = template.metadata.id
                val outputId = "$id-${language.name.lowercase()}"
                val dir = File(outDir, outputId)
                dir.deleteRecursively()
                engine.generate(
                    NewProjectSpec(
                        name = "Check" + id.split('-').joinToString("") { it.replaceFirstChar(Char::uppercase) },
                        packageName = "com.example.check." + id.replace("-", ""),
                        templateId = id,
                        language = language,
                        minSdk = 26,
                    ),
                    dir,
                )
                println("generated: $outputId -> ${dir.absolutePath}")
            }
        }

        val groovyJava = File(outDir, "existing-java-groovy")
        groovyJava.deleteRecursively()
        engine.generate(
            NewProjectSpec(
                name = "ExistingJavaGroovy",
                packageName = "com.example.existing.java",
                templateId = "empty-views",
                language = TemplateLanguage.JAVA,
                buildDsl = ProjectBuildDsl.GROOVY,
                minSdk = 26,
            ),
            groovyJava,
        )
        println("generated: existing-java-groovy -> ${groovyJava.absolutePath}")
    }

    private companion object {
        const val OUT_PROPERTY = "asl.templateCheck.out"
    }
}
