package com.example.androidstudiolite.feature.terminal
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.example.androidstudiolite.designsystem.component.buttons.AslIconButton
import com.example.androidstudiolite.designsystem.component.ide.AslTerminalBlock
import com.example.androidstudiolite.designsystem.theme.AslCode
import com.example.androidstudiolite.designsystem.theme.AslShape
import com.example.androidstudiolite.designsystem.theme.AslTheme
import com.example.androidstudiolite.feature.terminal.TerminalInteraction
import com.example.androidstudiolite.feature.terminal.TerminalUiState
import com.example.androidstudiolite.feature.terminal.TerminalViewModel

private val EXTRA_KEYS = listOf("Esc", "Ctrl", "Alt", "Tab", "/", "|", "←", "↑", "↓", "→")

@Composable
fun TerminalRoute(onBack: () -> Unit, viewModel: TerminalViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TerminalScreen(uiState = uiState, onInteraction = viewModel::onInteraction, onBack = onBack)
}

@Composable
private fun TerminalScreen(
    uiState: TerminalUiState,
    onInteraction: (TerminalInteraction) -> Unit,
    onBack: () -> Unit,
) {
    val colors = AslTheme.colors
    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.background(colors.bgElevated)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AslIconButton(icon = "arrow-left", contentDescription = "Back", onClick = onBack)
                    Row(
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(text = "Terminal", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        Text(
                            text = "· session ${uiState.sessionNumber}",
                            style = AslCode.codeTiny,
                            color = colors.textTertiary,
                        )
                    }
                    AslIconButton(icon = "plus", contentDescription = "New session", onClick = { onInteraction(TerminalInteraction.NewSession) })
                    AslIconButton(icon = "settings-2", contentDescription = "Terminal settings", onClick = {})
                }
                HorizontalDivider(color = colors.borderDefault, thickness = 1.dp)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(colors.terminalBg),
            ) {
                AslTerminalBlock(
                    lines = uiState.lines,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    shape = AslShape.none,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 18.dp)
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                ) {
                    Text(text = "$ ", style = AslCode.codeSmall, color = colors.terminalPrompt)
                    BasicTextField(
                        value = uiState.input,
                        onValueChange = { onInteraction(TerminalInteraction.InputChanged(it)) },
                        textStyle = AslCode.codeSmall.copy(color = colors.terminalStdout),
                        cursorBrush = SolidColor(colors.terminalPrompt),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onInteraction(TerminalInteraction.SubmitCommand) }),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Column(modifier = Modifier.background(colors.bgElevated)) {
                HorizontalDivider(color = colors.borderDefault, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    EXTRA_KEYS.forEach { key ->
                        ExtraKeyChip(label = key, onClick = { onInteraction(TerminalInteraction.ExtraKeyPressed(key)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtraKeyChip(label: String, onClick: () -> Unit) {
    val colors = AslTheme.colors
    Box(
        modifier = Modifier
            .height(36.dp)
            .defaultMinSize(minWidth = 44.dp)
            .background(colors.surfaceContainerHigh, AslShape.sm)
            .border(1.dp, colors.borderDefault, AslShape.sm)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = AslCode.codeSmall, color = colors.textPrimary)
    }
}
