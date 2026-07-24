package com.ahmadkharfan.androidstudiolite.data.ai

import com.ahmadkharfan.androidstudiolite.data.ai.agent.AgentProtocol
import com.ahmadkharfan.androidstudiolite.domain.model.AgentAction
import com.ahmadkharfan.androidstudiolite.domain.model.AgentToolResult
import com.ahmadkharfan.androidstudiolite.domain.model.AiAgentSettings
import com.ahmadkharfan.androidstudiolite.domain.model.AiProviderConfig
import com.ahmadkharfan.androidstudiolite.domain.model.ApiKeyStatus
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMessage
import com.ahmadkharfan.androidstudiolite.domain.model.ChatRole
import com.ahmadkharfan.androidstudiolite.domain.model.ChatThread

internal const val AGENT_MODEL_OUTPUT_LIMIT = 24000

internal fun AgentAction.pathOrNull(): String? = when (this) {
    is AgentAction.Search -> null
    is AgentAction.ListDir -> path
    is AgentAction.ReadFile -> path
    is AgentAction.CreateFile -> path
    is AgentAction.CreateDir -> path
    is AgentAction.EditFile -> path
    is AgentAction.Rename -> path
    is AgentAction.Move -> path
    is AgentAction.Delete -> path
}

internal fun AgentAction.summary(): String = when (this) {
    is AgentAction.ListDir -> "List $path"
    is AgentAction.ReadFile -> "Read $path"
    is AgentAction.Search -> "Search \"$query\""
    is AgentAction.CreateFile -> "Create $path"
    is AgentAction.CreateDir -> "New folder $path"
    is AgentAction.EditFile -> "Edit $path"
    is AgentAction.Rename -> "Rename $path -> $newName"
    is AgentAction.Move -> "Move $path -> $newParent"
    is AgentAction.Delete -> "Delete $path"
}

internal fun coalesceTurns(turns: List<LlmChatTurn>): List<LlmChatTurn> {
    val merged = ArrayList<LlmChatTurn>()
    for (turn in turns) {
        val last = merged.lastOrNull()
        if (last != null && last.role == turn.role) {
            merged[merged.lastIndex] = LlmChatTurn(turn.role, "${last.text}\n\n${turn.text}")
        } else {
            merged.add(turn)
        }
    }
    return merged
}

internal fun formatToolResults(results: List<AgentToolResult>, outputLimit: Int = AGENT_MODEL_OUTPUT_LIMIT): String =
    buildString {
        appendLine("TOOL RESULTS:")
        results.forEach { result ->
            val status = if (result.ok) "OK" else "ERROR"
            appendLine("[${result.action.tool} ${result.action.pathOrNull() ?: ""}] $status:")
            appendLine(result.output.take(outputLimit))
        }
    }

internal fun findPlanMessage(messages: List<ChatMessage>): ChatMessage? =
    messages.lastOrNull { it.role == ChatRole.AI && it.showPlanActions && it.text.isNotBlank() }
        ?: messages.lastOrNull { it.role == ChatRole.AI && AgentProtocol.isPlanLike(it.text) }

internal fun resolveChatProvider(settings: AiAgentSettings, thread: ChatThread?): AiProviderConfig? {
    if (!settings.enabled) return null
    val valid = settings.providers.filter { it.status == ApiKeyStatus.VALID }
    thread?.providerId?.let { id -> valid.firstOrNull { it.id == id } }?.let { return it }
    return settings.activeProvider()
}
