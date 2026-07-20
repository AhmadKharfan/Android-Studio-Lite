package com.ahmadkharfan.androidstudiolite.domain.model

/** Source language for a generated project. Compose templates require [KOTLIN]. */
enum class TemplateLanguage { KOTLIN, JAVA }

/** Build-script dialect for a generated project. ASL defaults to [KTS]. */
enum class ProjectBuildDsl { KTS, GROOVY }

/**
 * Everything the template engine needs to generate a project: the chosen template plus every user
 * option collected by the create-project wizard. Carried through
 * [com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository.createProject].
 *
 * Sensible defaults mirror Android Studio's new-project dialog (Kotlin + KTS + version catalog) and
 * are pinned to the ASL compatibility matrix (see the template engine's compat constants).
 */
data class NewProjectSpec(
    val name: String,
    val packageName: String,
    val templateId: String,
    val language: TemplateLanguage = TemplateLanguage.KOTLIN,
    val buildDsl: ProjectBuildDsl = ProjectBuildDsl.KTS,
    val minSdk: Int = 24,
    val targetSdk: Int = 34,
    val compileSdk: Int = 34,
    /**
     * Absolute directory to create the project in. When null (or not absolute) the repository uses
     * its default on-device projects root.
     */
    val saveLocation: String? = null,
)
