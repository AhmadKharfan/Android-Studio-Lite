package com.ahmadkharfan.androidstudiolite.domain.model

data class Project(
    val id: String,
    val name: String,
    val path: String,
    val language: String,
    val lastOpenedMillis: Long?,
)

data class CloneProgress(
    val fraction: Float?,
    val message: String,
    val clonedProjectId: String? = null,
)
