package com.ahmadkharfan.androidstudiolite.data.templates

import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec

data class LibrarySpec(
    val alias: String,
    val group: String,
    val name: String,
    val versionKey: String? = null,
    val version: String? = null,
) {
    val accessor: String get() = "libs." + alias.replace('-', '.')
}

data class PluginSpec(
    val alias: String,
    val id: String,
    val versionKey: String,
    val version: String,
) {
    val accessor: String get() = "libs.plugins." + alias.replace('-', '.')
}

data class DependencyRef(
    val configuration: String,
    val library: LibrarySpec,
    val isPlatform: Boolean = false,
)

data class RecipeFile(val relativePath: String, val content: String)

data class TemplateMetadata(
    val id: String,
    val name: String,
    val description: String,
    val thumbnail: String,
    val tags: List<String>,
)

interface Template {
    val metadata: TemplateMetadata

    val supportsJava: Boolean get() = true

    fun assemble(spec: NewProjectSpec, recipe: ProjectRecipe)
}
