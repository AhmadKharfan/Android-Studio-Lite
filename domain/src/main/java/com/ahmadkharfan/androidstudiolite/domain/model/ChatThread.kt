package com.ahmadkharfan.androidstudiolite.domain.model

/** A single conversation within a project. Threads are isolated per project and persisted to disk. */
data class ChatThread(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<ChatMessage>,
    val mode: ChatMode = ChatMode.AGENT,
    /** Provider id used for this thread; null falls back to the global default. */
    val providerId: String? = null,
    /** Model id used for this thread; null falls back to the provider's default model. */
    val model: String? = null,
)

/** Lightweight row for the chat history list. */
data class ChatThreadSummary(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messageCount: Int,
)

/** The active thread's mode + provider/model, for driving the chat controls. */
data class ChatThreadSelection(
    val threadId: String,
    val mode: ChatMode,
    val providerId: String?,
    val model: String?,
)
