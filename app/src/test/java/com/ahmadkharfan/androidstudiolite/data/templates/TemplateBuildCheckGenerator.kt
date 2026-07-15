package com.ahmadkharfan.androidstudiolite.data.templates

import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Writes every shipped template to disk so `tools/template_build_check.sh` can send each one to the
 * real build server and report which templates actually build.
 *
 * Not a test — it's the generator half of that harness, living in test sources because that's where
 * the template engine is reachable from a plain JVM (the engine itself is Android-free; only the
 * wrapper assets need a source, supplied here straight from the asset directory).
 *
 * Inert unless `-Dasl.templateCheck.out=<dir>` is set, so a normal test run doesn't write anything.
 */
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
            val id = template.metadata.id
            val dir = File(outDir, id)
            dir.deleteRecursively()
            engine.generate(
                NewProjectSpec(
                    // Package must be a legal, distinct id per template: they're built for real and
                    // an installed app is keyed by it.
                    name = "Check" + id.split('-').joinToString("") { it.replaceFirstChar(Char::uppercase) },
                    packageName = "com.example.check." + id.replace("-", ""),
                    templateId = id,
                    minSdk = 26,
                ),
                dir,
            )
            println("generated: $id -> ${dir.absolutePath}")
        }
    }

    private companion object {
        const val OUT_PROPERTY = "asl.templateCheck.out"
    }
}
