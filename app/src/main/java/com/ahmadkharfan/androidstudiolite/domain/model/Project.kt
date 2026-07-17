package com.ahmadkharfan.androidstudiolite.domain.model

data class Project(
    val id: String,
    val name: String,
    val path: String,
    val language: String,
    val lastOpenedMillis: Long?,
    /**
     * The applicationId/namespace the project was created with, used by the create-project wizard to
     * reject a duplicate package. Null for projects that predate this field, and for imported or
     * cloned projects (their package is only discoverable by reading their build script).
     */
    val packageName: String? = null,
    /** Whether the directory contains a Gradle settings file and can use build/run actions. */
    val buildable: Boolean = true,
)

data class CloneProgress(
    val fraction: Float?,
    val message: String,
    val clonedProjectId: String? = null,
)

/** Options supported by the real JGit clone operation. */
data class CloneOptions(
    val branch: String? = null,
    val depth: Int? = null,
    val singleBranch: Boolean = false,
    val recursiveSubmodules: Boolean = false,
)
