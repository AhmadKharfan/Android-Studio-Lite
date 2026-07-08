package com.example.androidstudiolite.domain.usecase

import com.example.androidstudiolite.domain.model.FileNode
import com.example.androidstudiolite.domain.repository.FileTreeRepository

class GetFileTreeUseCase(private val repository: FileTreeRepository) {
    suspend operator fun invoke(projectId: String): List<FileNode> = repository.getFileTree(projectId)
}
