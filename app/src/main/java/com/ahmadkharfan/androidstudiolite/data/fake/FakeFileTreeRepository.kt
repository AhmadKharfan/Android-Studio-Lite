package com.ahmadkharfan.androidstudiolite.data.fake

import com.ahmadkharfan.androidstudiolite.domain.model.FileNode
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileStatus
import com.ahmadkharfan.androidstudiolite.domain.repository.FileTreeRepository

class FakeFileTreeRepository : FileTreeRepository {
    override suspend fun getFileTree(projectId: String): List<FileNode> = listOf(
        FileNode(
            id = "app",
            name = "app",
            children = listOf(
                FileNode(
                    id = "app/src",
                    name = "src",
                    children = listOf(
                        FileNode(
                            id = "app/src/main",
                            name = "main",
                            children = listOf(
                                FileNode(
                                    id = "app/src/main/java",
                                    name = "java",
                                    children = listOf(
                                        FileNode(id = "MainActivity.kt", name = "MainActivity.kt", gitStatus = GitFileStatus.MODIFIED),
                                        FileNode(id = "MainViewModel.kt", name = "MainViewModel.kt", gitStatus = GitFileStatus.ADDED),
                                    ),
                                ),
                                FileNode(id = "AndroidManifest.xml", name = "AndroidManifest.xml"),
                            ),
                        ),
                    ),
                ),
                FileNode(id = "build.gradle.kts", name = "build.gradle.kts", gitStatus = GitFileStatus.MODIFIED),
            ),
        ),
        FileNode(id = "gradle", name = "gradle"),
        FileNode(id = "settings.gradle.kts", name = "settings.gradle.kts"),
        FileNode(id = "local.properties", name = "local.properties", gitStatus = GitFileStatus.UNTRACKED),
    )
}
