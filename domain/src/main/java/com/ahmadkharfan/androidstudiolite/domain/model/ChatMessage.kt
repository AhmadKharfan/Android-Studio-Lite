package com.ahmadkharfan.androidstudiolite.domain.model

enum class ChatRole { USER, AI }

/** NORMAL messages render as chat bubbles; THINKING messages render as a collapsible reasoning block. */
enum class ChatMessageKind { NORMAL, THINKING }

data class ChatCodeSnippet(val language: String, val code: String)

/** Lifecycle of an agent tool call as shown in the chat stream. */
enum class ToolCallStatus { PENDING, RUNNING, DONE, FAILED, REJECTED }

/**
 * A single filesystem tool invocation surfaced in the chat. When [status] is [ToolCallStatus.PENDING]
 * the agent loop is paused awaiting the user's Approve/Reject decision; [diffOld]/[diffNew] drive the
 * preview shown before a mutating action runs.
 */
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
    /** True while tokens are still streaming into this message. Never persisted as true. */
    val streaming: Boolean = false,
    /** When true, show Build / Review actions under a completed Plan-mode reply. */
    val showPlanActions: Boolean = false,
)
