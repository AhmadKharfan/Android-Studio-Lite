package com.example.androidstudiolite.domain.usecase

import com.example.androidstudiolite.domain.model.FolderTree
import com.example.androidstudiolite.domain.repository.FileSystemRepository

class GetFolderTreeUseCase(private val repository: FileSystemRepository) {
    suspend operator fun invoke(): FolderTree = repository.getFolderTree()
}
