package com.ahmadkharfan.androidstudiolite.data.templates

import com.ahmadkharfan.androidstudiolite.domain.model.ProjectTemplate
import com.ahmadkharfan.androidstudiolite.domain.repository.TemplateRepository

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
    thumbnail = thumbnail,
    tags = tags,
)
