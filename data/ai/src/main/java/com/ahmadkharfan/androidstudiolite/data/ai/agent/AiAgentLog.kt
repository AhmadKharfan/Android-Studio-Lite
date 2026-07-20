package com.ahmadkharfan.androidstudiolite.data.ai.agent

import android.util.Log

internal object AiAgentLog {
    const val TAG = "AslAiAgent"

    fun d(subtag: String, message: String) {
        Log.d(TAG, "[$subtag] $message")
    }

    fun i(subtag: String, message: String) {
        Log.i(TAG, "[$subtag] $message")
    }

    fun w(subtag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(TAG, "[$subtag] $message", throwable)
        } else {
            Log.w(TAG, "[$subtag] $message")
        }
    }

    fun e(subtag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, "[$subtag] $message", throwable)
        } else {
            Log.e(TAG, "[$subtag] $message")
        }
    }

    fun preview(raw: String, maxLen: Int = 800): String {
        val trimmed = raw.trim()
        if (trimmed.length <= maxLen) return trimmed
        return trimmed.take(maxLen) + "… (${trimmed.length} chars total)"
    }

    fun tail(raw: String, maxLen: Int = 200): String {
        val trimmed = raw.trim()
        if (trimmed.length <= maxLen) return trimmed
        return "… (${trimmed.length} chars) " + trimmed.takeLast(maxLen)
    }
}
