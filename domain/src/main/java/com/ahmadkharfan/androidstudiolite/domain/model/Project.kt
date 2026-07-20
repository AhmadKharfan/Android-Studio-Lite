package com.ahmadkharfan.androidstudiolite.domain.model

data class Project(
    val id: String,
    val name: String,
    val path: String,
    val language: String,
    val lastOpenedMillis: Long?,
    val packageName: String? = null,
    val buildable: Boolean = true,
)

data class CloneProgress(
    val fraction: Float?,
    val message: String,
    val clonedProjectId: String? = null,
)

data class CloneOptions(
    val branch: String? = null,
    val depth: Int? = null,
    val singleBranch: Boolean = false,
    val recursiveSubmodules: Boolean = false,
)
