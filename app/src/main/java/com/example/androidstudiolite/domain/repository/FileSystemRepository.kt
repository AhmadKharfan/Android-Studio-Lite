package com.example.androidstudiolite.domain.repository

import com.example.androidstudiolite.domain.model.FolderTree

interface FileSystemRepository {
    suspend fun getFolderTree(): FolderTree
}
