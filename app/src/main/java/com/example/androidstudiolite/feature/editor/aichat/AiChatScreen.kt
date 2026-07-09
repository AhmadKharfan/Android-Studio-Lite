package com.example.androidstudiolite.feature.editor.aichat
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.example.androidstudiolite.designsystem.animation.AslStaggeredAppear
import com.example.androidstudiolite.designsystem.animation.AslStateCrossfade
import com.example.androidstudiolite.designsystem.component.buttons.AslIconButton
import com.example.androidstudiolite.designsystem.component.content.AslChatBubble
import com.example.androidstudiolite.designsystem.component.content.AslChatCodeBlock
import com.example.androidstudiolite.designsystem.component.content.AslChatRole
import com.example.androidstudiolite.designsystem.component.content.AslEmptyState
import com.example.androidstudiolite.designsystem.component.inputs.AslTextField
import com.example.androidstudiolite.designsystem.component.navigation.AslToolWindowPanel
import com.example.androidstudiolite.designsystem.theme.AslTheme
import com.example.androidstudiolite.feature.editor.aichat.AiChatInteraction
import com.example.androidstudiolite.feature.editor.aichat.AiChatUiState
import com.example.androidstudiolite.feature.editor.aichat.ChatMessageUiModel
import com.example.androidstudiolite.feature.editor.aichat.AiChatViewModel

@Composable
fun AiChatRoute(
    onClose: () -> Unit,
    onOpenAiAgentSettings: () -> Unit,
    viewModel: AiChatViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AiChatScreen(
        uiState = uiState,
        onInteraction = viewModel::onInteraction,
        onClose = onClose,
        onOpenAiAgentSettings = onOpenAiAgentSettings,
    )
}

@Composable
private fun AiChatScreen(
    uiState: AiChatUiState,
    onInteraction: (AiChatInteraction) -> Unit,
    onClose: () -> Unit,
    onOpenAiAgentSettings: () -> Unit,
) {
    AslToolWindowPanel(title = "AI Agent", width = 300.dp, onClose = onClose, scrollable = false) {
        AslStateCrossfade(
            targetState = uiState.hasConfiguredProvider,
            modifier = Modifier.fillMaxSize(),
            label = "aiChatConfigured",
        ) { configured ->
            if (!configured) {
                AslEmptyState(
                    icon = "sparkles",
                    title = "No AI provider configured",
                    subtitle = "Add and validate an API key in Settings to start chatting.",
                    actionLabel = "Open AI Agent settings",
                    onAction = onOpenAiAgentSettings,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ChatContent(uiState = uiState, onInteraction = onInteraction)
            }
        }
    }
}

@Composable
private fun ChatContent(
    uiState: AiChatUiState,
    onInteraction: (AiChatInteraction) -> Unit,
) {
    val colors = AslTheme.colors
    Column(modifier = Modifier.fillMaxSize()) {
            val scrollState = rememberScrollState()
            LaunchedEffect(uiState.messages.size) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                uiState.messages.forEach { message ->
                    // Each newly-appended message fades + lifts in rather than popping into the list.
                    AslStaggeredAppear {
                        ChatMessageBubble(message = message, onInteraction = onInteraction)
                    }
                }
                if (uiState.sending) {
                    Text(text = "AI is typing…", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                }
            }
            HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AslTextField(
                    value = uiState.input,
                    onValueChange = { onInteraction(AiChatInteraction.InputChanged(it)) },
                    placeholder = "Ask the AI agent…",
                    modifier = Modifier.weight(1f),
                )
                AslIconButton(
                    icon = "send",
                    contentDescription = "Send",
                    onClick = { onInteraction(AiChatInteraction.Send) },
                    disabled = uiState.input.isBlank() || uiState.sending,
                )
            }
        }
    }

@Composable
private fun ChatMessageBubble(message: ChatMessageUiModel, onInteraction: (AiChatInteraction) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val colors = AslTheme.colors
    AslChatBubble(
        role = if (message.isUser) AslChatRole.User else AslChatRole.Ai,
        timestamp = message.timestamp,
    ) {
        Text(text = message.text, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
        if (message.code != null) {
            AslChatCodeBlock(
                code = message.code,
                language = message.codeLanguage ?: "text",
                applied = message.applied,
                onCopy = { clipboard.setText(AnnotatedString(message.code)) },
                onApply = { onInteraction(AiChatInteraction.MarkApplied(message.id)) },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
