package com.example.androidstudiolite.data.fake

import com.example.androidstudiolite.domain.model.ProjectTemplate
import com.example.androidstudiolite.domain.repository.TemplateRepository

class FakeTemplateRepository : TemplateRepository {
    override suspend fun getTemplates(): List<ProjectTemplate> = listOf(
        ProjectTemplate(
            id = "empty-compose",
            name = "Empty Activity",
            description = "A single Compose screen, no navigation.",
            icon = "smartphone",
            tags = listOf("Kotlin", "Compose"),
        ),
        ProjectTemplate(
            id = "empty-views",
            name = "Empty Views Activity",
            description = "A single classic-Views activity.",
            icon = "layout",
            tags = listOf("Kotlin", "Views"),
        ),
        ProjectTemplate(
            id = "bottom-nav",
            name = "Bottom Navigation",
            description = "Compose screen with a bottom nav bar and 3 destinations.",
            icon = "grid",
            tags = listOf("Kotlin", "Compose"),
        ),
        ProjectTemplate(
            id = "no-activity",
            name = "No Activity",
            description = "Empty project, add your own entry point.",
            icon = "folder",
            tags = listOf("Kotlin"),
        ),
    )
}
