package com.ahmadkharfan.androidstudiolite.domain.model

enum class ChatRole { USER, AI }

enum class ChatMessageKind { NORMAL, THINKING }

data class ChatCodeSnippet(val language: String, val code: String)

enum class ToolCallStatus { PENDING, RUNNING, DONE, FAILED, REJECTED }

data class ChatToolCall(
    val id: String,
    val tool: String,
    val path: String? = null,
    val summary: String,
    val diffOld: String? = null,
    val diffNew: String? = null,
    val status: ToolCallStatus,
    val resultText: String? = null,
    val mutating: Boolean = false,
)

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val timestamp: String,
    val codeSnippet: ChatCodeSnippet? = null,
    val applied: Boolean = false,
    val toolCall: ChatToolCall? = null,
    val kind: ChatMessageKind = ChatMessageKind.NORMAL,
    val streaming: Boolean = false,
    val showPlanActions: Boolean = false,
)
