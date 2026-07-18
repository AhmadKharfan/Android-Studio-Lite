package com.ahmadkharfan.androidstudiolite.domain.model

/**
 * How the agent behaves in a chat thread.
 * - [AGENT]: reads and edits files (full tool access).
 * - [ASK]: read-only; answers questions but never modifies the project.
 * - [PLAN]: read-only; investigates and produces a step-by-step implementation plan.
 */
enum class ChatMode { AGENT, ASK, PLAN }
