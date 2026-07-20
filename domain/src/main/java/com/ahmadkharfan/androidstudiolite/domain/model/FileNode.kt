package com.ahmadkharfan.androidstudiolite.domain.model

enum class GitFileStatus { MODIFIED, ADDED, DELETED, UNTRACKED, CONFLICTED }

data class FileNode(
    val id: String,
    val name: String,
    val children: List<FileNode>? = null,
    val gitStatus: GitFileStatus? = null,
)
