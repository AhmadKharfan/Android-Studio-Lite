package com.example.androidstudiolite.domain.model

enum class ChatRole { USER, AI }

data class ChatCodeSnippet(val language: String, val code: String)

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val timestamp: String,
    val codeSnippet: ChatCodeSnippet? = null,
    val applied: Boolean = false,
)
