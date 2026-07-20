package com.ahmadkharfan.androidstudiolite.data.templates

import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.TemplateLanguage
import java.io.File

class ProjectTemplateEngine(
    private val registry: TemplateRegistry = TemplateRegistry(),
    private val wrapperSource: GradleWrapperSource? = null,
) {

    fun generate(spec: NewProjectSpec, projectDir: File): GenerationResult {
        val template = registry.find(spec.templateId)
            ?: throw IllegalArgumentException("Unknown template: ${spec.templateId}")


        require(spec.language != TemplateLanguage.JAVA || template.supportsJava) {
            "${template.metadata.name} requires Kotlin"
        }
        val effective = spec.copy(packageName = spec.packageName.ifBlank { "com.example.app" })

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
