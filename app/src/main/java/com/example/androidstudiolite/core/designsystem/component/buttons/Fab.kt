package com.example.androidstudiolite.core.designsystem.component.buttons

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.core.designsystem.icon.AslIcon
import com.example.androidstudiolite.core.designsystem.theme.AslShape
import com.example.androidstudiolite.core.designsystem.theme.AslTheme

/** Fab.jsx — extended FAB, the thumb-zone primary action ("Quick Run"). One per screen, max. */
@Composable
fun AslFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Quick Run",
    icon: String = "play",
    loading: Boolean = false,
) {
    val colors = AslTheme.colors
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = AslShape.full,
        containerColor = colors.accentPrimary,
        contentColor = colors.accentOnPrimary,
        icon = {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
            } else {
                AslIcon(name = icon, size = 22.dp)
            }
        },
        text = { Text(text = label, style = MaterialTheme.typography.labelLarge) },
    )
}
