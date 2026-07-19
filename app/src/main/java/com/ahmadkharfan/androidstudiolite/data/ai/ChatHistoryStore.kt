package com.ahmadkharfan.androidstudiolite.data.ai

import android.content.Context
import com.ahmadkharfan.androidstudiolite.domain.model.ChatCodeSnippet
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMessage
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMessageKind
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMode
import com.ahmadkharfan.androidstudiolite.domain.model.ChatRole
import com.ahmadkharfan.androidstudiolite.domain.model.ChatThread
import com.ahmadkharfan.androidstudiolite.domain.model.ChatToolCall
import com.ahmadkharfan.androidstudiolite.domain.model.ToolCallStatus
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Snapshot of one project's chat threads plus which one is active. */
data class ProjectChats(val activeThreadId: String, val threads: List<ChatThread>)

/**
 * Persists each project's chat threads to a JSON file under `<filesDir>/ai_chats/<hash>.json`, so
 * conversations survive app restarts and stay isolated per project.
 */
class ChatHistoryStore(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "ai_chats").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun load(projectId: String): ProjectChats = withContext(Dispatchers.IO) {
        val file = fileFor(projectId)
        if (!file.exists()) return@withContext ProjectChats("", emptyList())
        runCatching {
            val dto = json.decodeFromString(ProjectChatsDto.serializer(), file.readText())
            ProjectChats(dto.activeThreadId, dto.threads.map { it.toDomain() })
        }.getOrElse { ProjectChats("", emptyList()) }
    }

    suspend fun save(projectId: String, data: ProjectChats) = withContext(Dispatchers.IO) {
        val dto = ProjectChatsDto(
            activeThreadId = data.activeThreadId,
            threads = data.threads.map { it.toDto() },
        )
        runCatching { fileFor(projectId).writeText(json.encodeToString(ProjectChatsDto.serializer(), dto)) }
        Unit
    }

    private fun fileFor(projectId: String): File = File(dir, "${hash(projectId)}.json")

    private fun hash(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)
}

@Serializable
private data class ProjectChatsDto(
    val activeThreadId: String = "",
    val threads: List<ThreadDto> = emptyList(),
)

@Serializable
private data class ThreadDto(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<MessageDto> = emptyList(),
    val mode: String = "AGENT",
    val providerId: String? = null,
    val model: String? = null,
)

@Serializable
private data class MessageDto(
    val id: String,
    val role: String,
    val text: String,
    val timestamp: String,
    val codeLanguage: String? = null,
    val code: String? = null,
    val applied: Boolean = false,
    val toolCall: ToolCallDto? = null,
    val kind: String = "NORMAL",
    val showPlanActions: Boolean = false,
)

@Serializable
private data class ToolCallDto(
    val id: String,
    val tool: String,
    val path: String? = null,
    val summary: String,
    val diffOld: String? = null,
    val diffNew: String? = null,
    val status: String,
    val resultText: String? = null,
    val mutating: Boolean = false,
)

private fun ThreadDto.toDomain() = ChatThread(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    messages = messages.map { it.toDomain() },
    mode = runCatching { ChatMode.valueOf(mode) }.getOrDefault(ChatMode.AGENT),
    providerId = providerId,
    model = model,
)

private fun MessageDto.toDomain() = ChatMessage(
    id = id,
    role = if (role == "USER") ChatRole.USER else ChatRole.AI,
    text = text,
    timestamp = timestamp,
    codeSnippet = if (code != null) ChatCodeSnippet(codeLanguage ?: "text", code) else null,
    applied = applied,
    toolCall = toolCall?.toDomain(),
    kind = runCatching { ChatMessageKind.valueOf(kind) }.getOrDefault(ChatMessageKind.NORMAL),
    streaming = false,
    showPlanActions = showPlanActions,
)

private fun ToolCallDto.toDomain() = ChatToolCall(
    id = id,
    tool = tool,
    path = path,
    summary = summary,
    diffOld = diffOld,
    diffNew = diffNew,
    status = runCatching { ToolCallStatus.valueOf(status) }.getOrDefault(ToolCallStatus.DONE),
    resultText = resultText,
    mutating = mutating,
)

private fun ChatThread.toDto() = ThreadDto(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    messages = messages.map { it.toDto() },
    mode = mode.name,
    providerId = providerId,
    model = model,
)

private fun ChatMessage.toDto() = MessageDto(
    id = id,
    role = role.name,
    text = text,
    timestamp = timestamp,
    codeLanguage = codeSnippet?.language,
    code = codeSnippet?.code,
    applied = applied,
    toolCall = toolCall?.toDto(),
    kind = kind.name,
    showPlanActions = showPlanActions,
)

private fun ChatToolCall.toDto() = ToolCallDto(
    id = id,
    tool = tool,
    path = path,
    summary = summary,
    diffOld = diffOld,
    diffNew = diffNew,
    // Persist only terminal statuses so a reopened thread never shows a stuck "Review" card.
    status = when (status) {
        ToolCallStatus.PENDING, ToolCallStatus.RUNNING -> ToolCallStatus.DONE.name
        else -> status.name
    },
    resultText = resultText,
    mutating = mutating,
)
