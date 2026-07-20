package com.ahmadkharfan.androidstudiolite.feature.editor.aichat
import androidx.compose.runtime.Immutable
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMessageKind
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMode
import com.ahmadkharfan.androidstudiolite.domain.model.ToolCallStatus

@Immutable
data class ChatToolCallUiModel(
    val id: String,
    val tool: String,
    val path: String?,
    val summary: String,
    val diffOld: String?,
    val diffNew: String?,
    val status: ToolCallStatus,
    val resultText: String?,
    val mutating: Boolean,
)

@Immutable
data class ChatMessageUiModel(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val timestamp: String,
    val codeLanguage: String? = null,
    val code: String? = null,
    val applied: Boolean = false,
    val toolCall: ChatToolCallUiModel? = null,
    val kind: ChatMessageKind = ChatMessageKind.NORMAL,
    val streaming: Boolean = false,
    val showPlanActions: Boolean = false,
)

@Immutable
data class ChatThreadUiModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val isActive: Boolean,
)

@Immutable
data class ChatProviderUiModel(
    val id: String,
    val name: String,
    val models: List<String>,
)

@Immutable
data class AiChatUiState(
    val hasConfiguredProvider: Boolean = true,
    val messages: List<ChatMessageUiModel> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val autoApply: Boolean = false,
    val threads: List<ChatThreadUiModel> = emptyList(),
    val showHistory: Boolean = false,
    val mode: ChatMode = ChatMode.AGENT,
    val activeThreadId: String = "",
    /** Effective provider id for the active thread (thread choice or global default). */
    val providerId: String = "",
    /** Effective model id for the active thread. */
    val model: String = "",
    /** Validated providers the user can switch between, each with its selectable models. */
    val providers: List<ChatProviderUiModel> = emptyList(),
    val showControls: Boolean = false,
    /** When set, the plan review sheet is open for this plan message id. */
    val planReviewMessageId: String? = null,
    val planReviewInput: String = "",
) {
    /** Models offered for the currently selected provider. */
    val availableModels: List<String>
        get() = providers.firstOrNull { it.id == providerId }?.models ?: emptyList()
}
