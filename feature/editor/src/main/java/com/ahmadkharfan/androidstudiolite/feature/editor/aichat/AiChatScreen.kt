package com.ahmadkharfan.androidstudiolite.feature.editor.aichat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.ahmadkharfan.androidstudiolite.feature.editor.R
import com.ahmadkharfan.androidstudiolite.designsystem.animation.AslStaggeredAppear
import com.ahmadkharfan.androidstudiolite.designsystem.animation.AslStateCrossfade
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslIconButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslChatBubble
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslChatCodeBlock
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslChatRole
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslEmptyState
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslMarkdownText
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslThinkingBlock
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslToolCallCard
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslToolCallState
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMessageKind
import com.ahmadkharfan.androidstudiolite.domain.model.ToolCallStatus
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslChip
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslChipKind
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslDropdown
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslDropdownOption
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSegmentedButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSegmentedOption
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSwitch
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextField
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslBottomSheet
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslToolWindowPanel
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.rememberAslToolWindowWidth
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.layout.aslImePadding
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMode
import com.ahmadkharfan.androidstudiolite.feature.editor.aichat.AiChatInteractionListener
import com.ahmadkharfan.androidstudiolite.feature.editor.aichat.AiChatUiState
import com.ahmadkharfan.androidstudiolite.feature.editor.aichat.ChatMessageUiModel
import com.ahmadkharfan.androidstudiolite.feature.editor.aichat.AiChatViewModel

@Composable
fun AiChatRoute(
    projectId: String,
    onClose: () -> Unit,
    onOpenAiAgentSettings: () -> Unit,
    activeFilePath: String? = null,
    viewModel: AiChatViewModel = koinViewModel(key = "ai-chat-$projectId") { parametersOf(projectId) },
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(activeFilePath) { viewModel.onActiveFileChanged(activeFilePath) }
    AiChatScreen(
        uiState = uiState,
        interactionListener = viewModel,
        onClose = onClose,
        onOpenAiAgentSettings = onOpenAiAgentSettings,
    )
}

@Composable
private fun AiChatScreen(
    uiState: AiChatUiState,
    interactionListener: AiChatInteractionListener,
    onClose: () -> Unit,
    onOpenAiAgentSettings: () -> Unit,
) {
    AslToolWindowPanel(
        title = stringResource(R.string.ai_chat_title),
        width = rememberAslToolWindowWidth(),
        onClose = onClose,
        scrollable = false,
        actions = {
            AslChip(
                label = stringResource(uiState.mode.labelRes()),
                kind = AslChipKind.Assist,
                icon = "sliders-horizontal",
                onClick = { interactionListener.onOpenControls() },
            )
            AslIconButton(
                icon = "plus",
                contentDescription = stringResource(R.string.ai_chat_new_chat),
                onClick = { interactionListener.onNewChat() },
            )
            AslIconButton(
                icon = "history",
                contentDescription = stringResource(R.string.ai_chat_history),
                active = uiState.showHistory,
                onClick = { interactionListener.onToggleHistory() },
            )
        },
    ) {
        if (uiState.showControls) {
            ChatControlsSheet(uiState = uiState, interactionListener = interactionListener)
        }
        if (uiState.planReviewMessageId != null) {
            PlanReviewSheet(uiState = uiState, interactionListener = interactionListener)
        }
        AslStateCrossfade(
            targetState = uiState.hasConfiguredProvider,
            modifier = Modifier.fillMaxSize(),
            label = "aiChatConfigured",
        ) { configured ->
            if (!configured) {
                AslEmptyState(
                    icon = "sparkles",
                    title = stringResource(R.string.ai_chat_no_provider_title),
                    subtitle = stringResource(R.string.ai_chat_no_provider_sub),
                    actionLabel = stringResource(R.string.ai_chat_open_settings),
                    onAction = onOpenAiAgentSettings,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                AslStateCrossfade(
                    targetState = uiState.showHistory,
                    modifier = Modifier.fillMaxSize(),
                    label = "aiChatHistory",
                ) { history ->
                    if (history) {
                        ChatHistoryList(uiState = uiState, interactionListener = interactionListener)
                    } else {
                        ChatContent(uiState = uiState, interactionListener = interactionListener)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanReviewSheet(
    uiState: AiChatUiState,
    interactionListener: AiChatInteractionListener,
) {
    val colors = AslTheme.colors
    AslBottomSheet(
        onDismiss = { interactionListener.onDismissPlanReview() },
        title = stringResource(R.string.ai_plan_review_sheet_title),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.ai_plan_review_sheet_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            AslTextField(
                value = uiState.planReviewInput,
                onValueChange = { interactionListener.onPlanReviewInputChanged(it) },
                placeholder = stringResource(R.string.ai_plan_review_sheet_placeholder),
            )
            Text(
                text = stringResource(R.string.ai_plan_review_sheet_hint),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
            AslButton(
                label = stringResource(R.string.ai_plan_review_submit),
                icon = "search",
                onClick = { interactionListener.onSubmitPlanReview() },
                disabled = uiState.sending,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ChatControlsSheet(
    uiState: AiChatUiState,
    interactionListener: AiChatInteractionListener,
) {
    val colors = AslTheme.colors
    AslBottomSheet(
        onDismiss = { interactionListener.onDismissControls() },
        title = stringResource(R.string.ai_chat_controls_title),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.ai_chat_mode),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
                AslSegmentedButton(
                    options = listOf(
                        AslSegmentedOption(stringResource(R.string.ai_chat_mode_agent), ChatMode.AGENT.name, "wrench"),
                        AslSegmentedOption(stringResource(R.string.ai_chat_mode_ask), ChatMode.ASK.name, "message-square"),
                        AslSegmentedOption(stringResource(R.string.ai_chat_mode_plan), ChatMode.PLAN.name, "list"),
                    ),
                    value = uiState.mode.name,
                    onValueChange = { interactionListener.onModeSelected(ChatMode.valueOf(it)) },
                    fullWidth = true,
                )
                Text(
                    text = stringResource(uiState.mode.descriptionRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }

            if (uiState.providers.size > 1) {
                AslDropdown(
                    label = stringResource(R.string.ai_chat_provider),
                    options = uiState.providers.map { AslDropdownOption(it.name, it.id) },
                    value = uiState.providerId,
                    onValueChange = { interactionListener.onProviderSelected(it) },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.ai_chat_model),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    AslIconButton(
                        icon = "refresh-cw",
                        contentDescription = stringResource(R.string.ai_chat_refresh_models),
                        size = 32.dp,
                        iconSize = 16.dp,
                        onClick = { interactionListener.onRefreshModels() },
                    )
                }
                if (uiState.availableModels.isNotEmpty()) {
                    AslDropdown(
                        options = uiState.availableModels.map { AslDropdownOption(it, it) },
                        value = uiState.model,
                        onValueChange = { interactionListener.onModelSelected(it) },
                    )
                }
                ChatCustomModelField(current = uiState.model, onApply = { interactionListener.onModelSelected(it) })
            }

            AslSwitch(
                label = stringResource(
                    if (uiState.autoApply) R.string.ai_chat_auto_apply_on else R.string.ai_chat_auto_apply_off,
                ),
                checked = uiState.autoApply,
                onCheckedChange = { interactionListener.onToggleAutoApply(it) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ChatCustomModelField(current: String, onApply: (String) -> Unit) {
    var text by remember(current) { mutableStateOf(current) }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AslTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = stringResource(R.string.ai_chat_custom_model),
            modifier = Modifier.weight(1f),
        )
        AslButton(
            label = stringResource(R.string.ai_chat_set_model),
            onClick = { if (text.isNotBlank()) onApply(text.trim()) },
            variant = AslButtonVariant.Secondary,
            disabled = text.isBlank() || text == current,
        )
    }
}

@Composable
private fun ChatHistoryList(
    uiState: AiChatUiState,
    interactionListener: AiChatInteractionListener,
) {
    val colors = AslTheme.colors
    if (uiState.threads.isEmpty()) {
        AslEmptyState(
            icon = "history",
            title = stringResource(R.string.ai_chat_history_empty_title),
            subtitle = stringResource(R.string.ai_chat_history_empty_sub),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        uiState.threads.forEach { thread ->
            ChatHistoryRow(
                thread = thread,
                onSelect = { interactionListener.onSelectThread(thread.id) },
                onDelete = { interactionListener.onDeleteThread(thread.id) },
            )
            HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
        }
    }
}

@Composable
private fun ChatHistoryRow(
    thread: ChatThreadUiModel,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AslTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .background(if (thread.isActive) colors.surfaceContainerHigh else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AslIcon(
            name = if (thread.isActive) "message-square" else "clock",
            contentDescription = null,
            tint = if (thread.isActive) colors.accentPrimary else colors.textTertiary,
            size = 18.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = thread.title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = thread.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
        }
        AslIconButton(
            icon = "trash-2",
            contentDescription = stringResource(R.string.ai_chat_delete_chat),
            size = 32.dp,
            iconSize = 16.dp,
            onClick = onDelete,
        )
    }
}

@Composable
private fun ChatContent(
    uiState: AiChatUiState,
    interactionListener: AiChatInteractionListener,
) {
    val colors = AslTheme.colors
    Column(modifier = Modifier.fillMaxSize().aslImePadding()) {
        ChatMessageList(uiState = uiState, interactionListener = interactionListener, modifier = Modifier.weight(1f))
        HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
        ChatInputRow(uiState = uiState, interactionListener = interactionListener)
    }
}

@Composable
private fun ChatMessageList(
    uiState: AiChatUiState,
    interactionListener: AiChatInteractionListener,
    modifier: Modifier = Modifier,
) {
    val colors = AslTheme.colors
    val scrollState = rememberScrollState()

    val streamTick = uiState.messages.lastOrNull()?.let { "${it.id}:${it.text.length}:${it.streaming}" }
    LaunchedEffect(uiState.messages.size, streamTick) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    val hasLiveContent = uiState.messages.any { it.streaming || it.kind == ChatMessageKind.THINKING }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        uiState.messages.forEach { message ->
            AslStaggeredAppear {
                when {
                    message.toolCall != null ->
                        ToolCallMessage(toolCall = message.toolCall, interactionListener = interactionListener)
                    message.kind == ChatMessageKind.THINKING ->
                        AslThinkingBlock(
                            text = message.text,
                            streaming = message.streaming,
                            thinkingLabel = stringResource(R.string.ai_chat_thinking),
                            thoughtLabel = stringResource(R.string.ai_chat_thought),
                        )
                    else -> ChatMessageBubble(message = message, interactionListener = interactionListener)
                }
            }
        }

        if (uiState.sending && !hasLiveContent) {
            Text(
                text = stringResource(R.string.ai_chat_typing),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun ChatInputRow(
    uiState: AiChatUiState,
    interactionListener: AiChatInteractionListener,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AslTextField(
            value = uiState.input,
            onValueChange = { interactionListener.onInputChanged(it) },
            placeholder = stringResource(R.string.ai_chat_input_hint),
            modifier = Modifier.weight(1f),
        )
        AslIconButton(
            icon = "send",
            contentDescription = stringResource(R.string.ai_chat_send),
            onClick = { interactionListener.onSend() },
            disabled = uiState.input.isBlank() || uiState.sending,
        )
    }
}

@Composable
private fun ToolCallMessage(toolCall: ChatToolCallUiModel, interactionListener: AiChatInteractionListener) {
    AslToolCallCard(
        title = toolCall.summary,
        icon = toolCall.tool.toToolIcon(),
        state = toolCall.status.toCardState(),
        diffOld = toolCall.diffOld,
        diffNew = toolCall.diffNew,
        result = toolCall.resultText,
        approveLabel = stringResource(R.string.ai_tool_approve),
        rejectLabel = stringResource(R.string.ai_tool_reject),
        onApprove = { interactionListener.onApproveTool(toolCall.id) },
        onReject = { interactionListener.onRejectTool(toolCall.id) },
    )
}

private fun String.toToolIcon(): String = when (this) {
    "list_dir" -> "folder"
    "read_file" -> "file-text"
    "search" -> "search"
    "create_file" -> "file-plus"
    "create_dir" -> "folder-plus"
    "edit_file" -> "pencil"
    "rename" -> "text-cursor-input"
    "move" -> "folder-input"
    "delete" -> "trash-2"
    else -> "wrench"
}

private fun ToolCallStatus.toCardState(): AslToolCallState = when (this) {
    ToolCallStatus.PENDING -> AslToolCallState.Pending
    ToolCallStatus.RUNNING -> AslToolCallState.Running
    ToolCallStatus.DONE -> AslToolCallState.Done
    ToolCallStatus.FAILED -> AslToolCallState.Failed
    ToolCallStatus.REJECTED -> AslToolCallState.Rejected
}

private fun ChatMode.labelRes(): Int = when (this) {
    ChatMode.AGENT -> R.string.ai_chat_mode_agent
    ChatMode.ASK -> R.string.ai_chat_mode_ask
    ChatMode.PLAN -> R.string.ai_chat_mode_plan
}

private fun ChatMode.descriptionRes(): Int = when (this) {
    ChatMode.AGENT -> R.string.ai_chat_mode_agent_desc
    ChatMode.ASK -> R.string.ai_chat_mode_ask_desc
    ChatMode.PLAN -> R.string.ai_chat_mode_plan_desc
}

@Composable
private fun ChatMessageBubble(message: ChatMessageUiModel, interactionListener: AiChatInteractionListener) {
    val clipboard = LocalClipboardManager.current
    val colors = AslTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AslChatBubble(
            role = if (message.isUser) AslChatRole.User else AslChatRole.Ai,
            timestamp = message.timestamp,
        ) {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (message.isUser) {
                        Text(text = message.text, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                    } else {
                        AslMarkdownText(
                            markdown = message.text,
                            onCopyCode = { clipboard.setText(AnnotatedString(it)) },
                        )
                    }
                    if (message.code != null) {
                        AslChatCodeBlock(
                            code = message.code,
                            language = message.codeLanguage ?: "text",
                            applied = message.applied,
                            onCopy = { clipboard.setText(AnnotatedString(message.code)) },
                            onApply = { interactionListener.onMarkApplied(message.id) },
                        )
                    }
                }
            }
        }
        if (message.showPlanActions && !message.isUser) {
            PlanActionRow(
                enabled = !message.streaming,
                onReview = { interactionListener.onPlanReview(message.id) },
                onBuild = { interactionListener.onPlanBuild(message.id) },
            )
        }
    }
}

@Composable
private fun PlanActionRow(
    enabled: Boolean,
    onReview: () -> Unit,
    onBuild: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AslButton(
            label = stringResource(R.string.ai_plan_review),
            variant = AslButtonVariant.Secondary,
            icon = "search",
            onClick = onReview,
            disabled = !enabled,
            modifier = Modifier.weight(1f),
        )
        AslButton(
            label = stringResource(R.string.ai_plan_build),
            variant = AslButtonVariant.Primary,
            icon = "hammer",
            onClick = onBuild,
            disabled = !enabled,
            modifier = Modifier.weight(1f),
        )
    }
}
