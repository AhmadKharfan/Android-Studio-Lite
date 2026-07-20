package com.ahmadkharfan.androidstudiolite.data.templates

import com.ahmadkharfan.androidstudiolite.domain.model.ProjectTemplate
import com.ahmadkharfan.androidstudiolite.domain.repository.TemplateRepository

class RealTemplateRepository(
    private val registry: TemplateRegistry = TemplateRegistry(),
) : TemplateRepository {

    override suspend fun getTemplates(): List<ProjectTemplate> =
        registry.templates.map { it.toProjectTemplate() }
}

private fun Template.toProjectTemplate() = ProjectTemplate(
    id = metadata.id,
    name = metadata.name,
    description = metadata.description,
    thumbnail = metadata.thumbnail,
    tags = metadata.tags,
    supportsJava = supportsJava,
)
