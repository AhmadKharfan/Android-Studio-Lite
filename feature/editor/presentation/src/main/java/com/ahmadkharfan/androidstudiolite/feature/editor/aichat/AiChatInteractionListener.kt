package com.ahmadkharfan.androidstudiolite.feature.editor.aichat

import com.ahmadkharfan.androidstudiolite.domain.model.ChatMode

interface AiChatInteractionListener {
    fun onInputChanged(value: String)
    fun onSend()
    fun onMarkApplied(messageId: String)
    fun onApproveTool(toolCallId: String)
    fun onRejectTool(toolCallId: String)
    fun onToggleAutoApply(enabled: Boolean)
    fun onNewChat()
    fun onToggleHistory()
    fun onSelectThread(threadId: String)
    fun onDeleteThread(threadId: String)
    fun onOpenControls()
    fun onDismissControls()
    fun onModeSelected(mode: ChatMode)
    fun onProviderSelected(providerId: String)
    fun onModelSelected(model: String)
    fun onRefreshModels()
    fun onPlanBuild(planMessageId: String)
    fun onPlanReview(planMessageId: String)
    fun onPlanReviewInputChanged(value: String)
    fun onDismissPlanReview()
    fun onSubmitPlanReview()
}
