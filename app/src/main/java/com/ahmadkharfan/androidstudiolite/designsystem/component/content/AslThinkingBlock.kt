package com.ahmadkharfan.androidstudiolite.designsystem.component.content

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

/**
 * Cursor-style collapsible reasoning block. Auto-expands while [streaming] and collapses when done;
 * the user can toggle manually afterward.
 */
@Composable
fun AslThinkingBlock(
    text: String,
    streaming: Boolean,
    thinkingLabel: String,
    thoughtLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = AslTheme.colors
    var expanded by remember { mutableStateOf(streaming) }
    LaunchedEffect(streaming) {
        expanded = streaming
    }
    val pulse = rememberInfiniteTransition(label = "thinkingPulse")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (streaming) 0.45f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "thinkingAlpha",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp)
                .alpha(if (streaming) alpha else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AslIcon(
                name = if (expanded) "chevron-down" else "chevron-right",
                size = 16.dp,
                tint = colors.textTertiary,
            )
            Text(
                text = if (streaming) thinkingLabel else thoughtLabel,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textTertiary,
            )
        }
        AnimatedVisibility(
            visible = expanded && text.isNotBlank(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            SelectionContainer {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(start = 22.dp, bottom = 4.dp),
                )
            }
        }
    }
}
