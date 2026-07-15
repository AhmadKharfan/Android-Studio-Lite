package com.ahmadkharfan.androidstudiolite.data.templates

import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.TemplateLanguage
import java.io.File

/**
 * Turns a [NewProjectSpec] into a real Gradle project on disk by resolving the chosen [Template] from
 * the [TemplateRegistry], letting it populate a [ProjectRecipe], and flushing the assembled project
 * (KTS + version catalog, pinned to the compat matrix, wrapper binaries included) to [projectDir].
 *
 * Replaces the placeholder `ProjectScaffold`. Written fresh for ASL.
 */
class ProjectTemplateEngine(
    private val registry: TemplateRegistry = TemplateRegistry(),
    private val wrapperSource: GradleWrapperSource? = null,
) {

    /** Generates [spec]'s project into [projectDir], returning the resolved template's language. */
    fun generate(spec: NewProjectSpec, projectDir: File): GenerationResult {
        val template = registry.find(spec.templateId)
            ?: throw IllegalArgumentException("Unknown template: ${spec.templateId}")

        // A template that can't be authored in the requested language falls back to Kotlin.
        val effective = if (spec.language == TemplateLanguage.JAVA && !template.supportsJava) {
            spec.copy(language = TemplateLanguage.KOTLIN)
        } else {
            spec
        }.copy(packageName = spec.packageName.ifBlank { "com.example.app" })

        val recipe = ProjectRecipe(effective)
        template.assemble(effective, recipe)

        projectDir.mkdirs()
        recipe.writeTo(projectDir, wrapperSource)
        return GenerationResult(templateId = template.metadata.id, language = effective.language)
    }
}

data class GenerationResult(
    val templateId: String,
    val language: TemplateLanguage,
)
