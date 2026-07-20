package com.ahmadkharfan.androidstudiolite.domain.model

enum class TemplateLanguage { KOTLIN, JAVA }

enum class ProjectBuildDsl { KTS, GROOVY }

data class NewProjectSpec(
    val name: String,
    val packageName: String,
    val templateId: String,
    val language: TemplateLanguage = TemplateLanguage.KOTLIN,
    val buildDsl: ProjectBuildDsl = ProjectBuildDsl.KTS,
    val minSdk: Int = 24,
    val targetSdk: Int = 34,
    val compileSdk: Int = 34,
    val saveLocation: String? = null,
)
