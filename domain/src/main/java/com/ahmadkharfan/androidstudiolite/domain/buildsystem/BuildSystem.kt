package com.ahmadkharfan.androidstudiolite.domain.buildsystem

import java.io.File
import kotlinx.coroutines.flow.Flow

interface BuildSystem {

    suspend fun sync(projectRoot: File): ProjectModel

    fun build(request: BuildRequest): Flow<BuildEvent>

    fun attach(buildId: String, projectRoot: File): Flow<BuildEvent>

    fun cancel()
}
