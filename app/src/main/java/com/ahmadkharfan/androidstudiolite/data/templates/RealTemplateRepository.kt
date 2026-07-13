package com.ahmadkharfan.androidstudiolite.data.templates

import com.ahmadkharfan.androidstudiolite.domain.model.ProjectTemplate
import com.ahmadkharfan.androidstudiolite.domain.repository.TemplateRepository

/**
 * Real [TemplateRepository] exposing the shipped [TemplateRegistry] templates' metadata to the
 * create-project wizard. Generation itself goes through [ProjectTemplateEngine]; both read the same
 * registry so the picker and the generator stay in lockstep. Replaces `FakeTemplateRepository`.
 */
class RealTemplateRepository(
    private val registry: TemplateRegistry = TemplateRegistry(),
) : TemplateRepository {

    override suspend fun getTemplates(): List<ProjectTemplate> =
        registry.templates.map { it.metadata.toProjectTemplate() }
}

private fun TemplateMetadata.toProjectTemplate() = ProjectTemplate(
    id = id,
    name = name,
    description = description,
    icon = icon,
    tags = tags,
)
