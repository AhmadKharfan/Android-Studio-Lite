package com.ahmadkharfan.androidstudiolite.data.ai.agent

import com.ahmadkharfan.androidstudiolite.domain.model.AgentAction
import com.ahmadkharfan.androidstudiolite.domain.model.AgentToolResult
import java.io.File

interface AgentTools {
    suspend fun outline(projectId: String, maxEntries: Int = 250): String
    suspend fun sourcePackagePrefix(projectId: String): String?
    suspend fun projectLanguage(projectId: String): String
    suspend fun projectRoot(projectId: String): File
    suspend fun readTextOrNull(projectId: String, relativePath: String): String?
    suspend fun run(projectId: String, action: AgentAction): AgentToolResult
    fun normalizeAction(root: File, action: AgentAction): AgentAction
}
