package com.example.androidstudiolite.domain.model

enum class GitFileStatus { MODIFIED, ADDED, DELETED, UNTRACKED }

data class FileNode(
    val id: String,
    val name: String,
    val children: List<FileNode>? = null,
    val gitStatus: GitFileStatus? = null,
)
