package com.ahmadkharfan.androidstudiolite.domain.model

data class FolderNode(
    val id: String,
    val name: String,
    val children: List<FolderNode>? = null,
)

data class FolderTree(
    val breadcrumb: List<String>,
    val items: List<FolderNode>,
)
