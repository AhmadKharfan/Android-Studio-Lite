package com.example.androidstudiolite.data.fake

import com.example.androidstudiolite.domain.model.FolderNode
import com.example.androidstudiolite.domain.model.FolderTree
import com.example.androidstudiolite.domain.repository.FileSystemRepository

class FakeFileSystemRepository : FileSystemRepository {
    override suspend fun getFolderTree(): FolderTree = FolderTree(
        breadcrumb = listOf("storage", "emulated", "0"),
        items = listOf(
            FolderNode(
                id = "projects",
                name = "projects",
                children = listOf(
                    FolderNode(id = "myapp", name = "MyApplication"),
                    FolderNode(id = "weather", name = "WeatherWidget"),
                ),
            ),
            FolderNode(id = "documents", name = "Documents"),
            FolderNode(id = "download", name = "Download"),
            FolderNode(id = "dcim", name = "DCIM"),
        ),
    )
}
