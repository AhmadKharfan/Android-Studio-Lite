package com.example.androidstudiolite.designsystem.component.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.designsystem.theme.AslTheme

enum class AslChatRole { User, Ai }

/** ChatBubble.jsx — asymmetric-corner message bubble; the "tail" corner points at the sender. */
@Composable
fun AslChatBubble(
    role: AslChatRole,
    modifier: Modifier = Modifier,
    timestamp: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AslTheme.colors
    val isUser = role == AslChatRole.User
    val shape = if (isUser) {
        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 4.dp, bottomStart = 12.dp)
    } else {
        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 4.dp)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(if (isUser) colors.accentPrimaryContainer else colors.surfaceContainerLow, shape)
                .then(if (!isUser) Modifier.border(1.dp, colors.borderSubtle, shape) else Modifier)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            content = content,
        )
        if (timestamp != null) {
            Text(
                text = timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}
