package com.example.androidstudiolite.domain.model

data class ProjectTemplate(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val tags: List<String>,
)
