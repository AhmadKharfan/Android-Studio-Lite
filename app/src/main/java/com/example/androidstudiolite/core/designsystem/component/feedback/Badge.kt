package com.example.androidstudiolite.core.designsystem.component.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidstudiolite.core.designsystem.theme.AslTheme

enum class AslBadgeTone { Error, Accent, Neutral }

/** Badge.jsx — notification count dot for rail icons and tabs. Omit count for a bare 8dp dot. */
@Composable
fun AslBadge(
    modifier: Modifier = Modifier,
    count: String? = null,
    tone: AslBadgeTone = AslBadgeTone.Error,
) {
    val colors = AslTheme.colors
    val bg = when (tone) {
        AslBadgeTone.Accent -> colors.accentPrimary
        AslBadgeTone.Neutral -> colors.textTertiary
        AslBadgeTone.Error -> colors.error
    }
    if (count == null) {
        Box(modifier = modifier.size(8.dp).background(bg, CircleShape))
    } else {
        Box(
            modifier = modifier
                .sizeIn(minWidth = 16.dp, minHeight = 16.dp)
                .background(bg, CircleShape)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count,
                color = Color.White,
                fontSize = 10.sp,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            )
        }
    }
}
