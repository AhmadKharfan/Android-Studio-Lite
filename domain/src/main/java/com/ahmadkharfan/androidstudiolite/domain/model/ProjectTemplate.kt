package com.ahmadkharfan.androidstudiolite.domain.model

data class ProjectTemplate(
    val id: String,
    val name: String,
    val description: String,
    val thumbnail: String,
    val tags: List<String>,
)
