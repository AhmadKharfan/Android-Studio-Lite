package com.example.androidstudiolite.core.designsystem.component.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.core.designsystem.theme.AslCode
import com.example.androidstudiolite.core.designsystem.theme.AslShape
import com.example.androidstudiolite.core.designsystem.theme.AslTheme

enum class AslTerminalLineKind { Stdout, Stderr, Cmd, Success }

data class AslTerminalLine(val text: String, val kind: AslTerminalLineKind = AslTerminalLineKind.Stdout)

/** TerminalBlock.jsx — always-dark bg, emerald prompt, colored stdout/stderr streams. Stateless renderer. */
@Composable
fun AslTerminalBlock(
    lines: List<AslTerminalLine>,
    modifier: Modifier = Modifier,
    prompt: String = "$",
    shape: Shape = AslShape.sm,
) {
    val colors = AslTheme.colors
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .clip(shape)
            .background(colors.terminalBg)
            .padding(vertical = 12.dp, horizontal = 14.dp),
    ) {
        items(lines) { line ->
            val textColor = when (line.kind) {
                AslTerminalLineKind.Stderr -> colors.terminalStderr
                AslTerminalLineKind.Success -> colors.terminalPrompt
                AslTerminalLineKind.Cmd, AslTerminalLineKind.Stdout -> colors.terminalStdout
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 18.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                if (line.kind == AslTerminalLineKind.Cmd) {
                    Text(text = "$prompt ", style = AslCode.codeSmall, color = colors.terminalPrompt)
                }
                Text(
                    text = line.text,
                    style = AslCode.codeSmall,
                    color = textColor,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}
