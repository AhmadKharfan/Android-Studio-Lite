package com.example.androidstudiolite.domain.model

data class GitChange(val path: String, val status: GitFileStatus)

enum class GitDiffKind { ADDED, REMOVED, MODIFIED, CONTEXT }

data class GitDiffLine(
    val kind: GitDiffKind,
    val text: String,
    val oldNo: Int? = null,
    val newNo: Int? = null,
)

data class GitState(
    val branch: String,
    val changes: List<GitChange>,
    val commitMessage: String = "",
    val committing: Boolean = false,
)
