package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.FileNode

interface FileTreeRepository {
    suspend fun getFileTree(projectId: String): List<FileNode>
}
