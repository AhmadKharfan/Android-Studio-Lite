package com.example.androidstudiolite.domain.repository

import com.example.androidstudiolite.domain.model.FileNode

interface FileTreeRepository {
    suspend fun getFileTree(projectId: String): List<FileNode>
}
