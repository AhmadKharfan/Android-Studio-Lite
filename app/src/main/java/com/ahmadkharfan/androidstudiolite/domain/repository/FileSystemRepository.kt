package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.FolderTree

interface FileSystemRepository {
    suspend fun getFolderTree(): FolderTree
}
