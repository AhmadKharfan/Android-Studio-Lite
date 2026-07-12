package com.ahmadkharfan.androidstudiolite.feature.editor.aichat

interface AiChatInteractionListener {
    fun onInputChanged(value: String)
    fun onSend()
    fun onMarkApplied(messageId: String)
}
