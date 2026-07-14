package com.ahmadkharfan.androidstudiolite.feature.terminal
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslIconButton
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

// On-screen helper keys for things a soft keyboard makes awkward (Esc, arrows, Ctrl-C, pipes).
private val EXTRA_KEYS = listOf("Esc", "Tab", "Ctrl+C", "←", "↑", "↓", "→", "/", "|", "~", "-")

@Composable
fun TerminalRoute(onBack: () -> Unit, viewModel: TerminalViewModel = koinViewModel()) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    TerminalScreen(uiState = uiState, interactionListener = viewModel, onBack = onBack)
}

@Composable
private fun TerminalScreen(
    uiState: TerminalUiState,
    interactionListener: TerminalInteractionListener,
    onBack: () -> Unit,
) {
    val colors = AslTheme.colors
    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TerminalTopBar(uiState = uiState, interactionListener = interactionListener, onBack = onBack, colors = colors)
            TerminalEmulatorView(
                screen = uiState.screen,
                background = colors.terminalBg,
                foreground = colors.terminalStdout,
                cursorColor = colors.terminalPrompt,
                onKey = { interactionListener.onKeyInput(it) },
                onSpecialKey = { interactionListener.onSpecialKey(it) },
                onResize = { rows, cols -> interactionListener.onResize(rows, cols) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            TerminalExtraKeysRow(interactionListener = interactionListener, colors = colors)
        }
    }
}

@Composable
private fun TerminalTopBar(
    uiState: TerminalUiState,
    interactionListener: TerminalInteractionListener,
    onBack: () -> Unit,
    colors: AslColorScheme,
) {
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
                    text = "· session ${uiState.sessionNumber}${if (!uiState.running) " · exited" else ""}",
                    style = AslCode.codeTiny,
                    color = colors.textTertiary,
                )
            }
            AslIconButton(icon = "plus", contentDescription = "New session", onClick = { interactionListener.onNewSession() })
            AslIconButton(icon = "settings-2", contentDescription = "Terminal settings", onClick = {})
        }
        HorizontalDivider(color = colors.borderDefault, thickness = 1.dp)
    }
}

@Composable
private fun TerminalExtraKeysRow(
    interactionListener: TerminalInteractionListener,
    colors: AslColorScheme,
) {
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
                ExtraKeyChip(label = key, onClick = { interactionListener.onExtraKeyPressed(key) })
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
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = AslCode.codeSmall, color = colors.textPrimary)
    }
}
