package com.ahmadkharfan.androidstudiolite.domain.model

data class ProjectTemplate(
    val id: String,
    val name: String,
    val description: String,
    /**
     * Key for the template's thumbnail artwork, resolved to a drawable by the picker. A key rather
     * than a resource id so this model stays free of Android resources.
     */
    val thumbnail: String,
    val tags: List<String>,
)
