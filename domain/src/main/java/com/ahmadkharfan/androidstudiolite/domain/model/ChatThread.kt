package com.ahmadkharfan.androidstudiolite.domain.model

data class ChatThread(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<ChatMessage>,
    val mode: ChatMode = ChatMode.AGENT,
    val providerId: String? = null,
    val model: String? = null,
)

data class ChatThreadSummary(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messageCount: Int,
)

data class ChatThreadSelection(
    val threadId: String,
    val mode: ChatMode,
    val providerId: String?,
    val model: String?,
)
