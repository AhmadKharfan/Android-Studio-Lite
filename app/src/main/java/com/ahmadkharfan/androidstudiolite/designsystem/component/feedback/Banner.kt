package com.ahmadkharfan.androidstudiolite.designsystem.component.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

enum class AslBannerTone { Info, Warning, Error, Success }

private fun toneIcon(tone: AslBannerTone): String = when (tone) {
    AslBannerTone.Info -> "info"
    AslBannerTone.Warning -> "triangle-alert"
    AslBannerTone.Error -> "octagon-alert"
    AslBannerTone.Success -> "check-circle-2"
}

/** Banner.jsx — inline info/warning/error/success strip, optional action + dismiss. */
@Composable
fun AslBanner(
    message: String,
    modifier: Modifier = Modifier,
    tone: AslBannerTone = AslBannerTone.Info,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    onDismiss: (() -> Unit)? = null,
) {
    val colors = AslTheme.colors
    val (bg, fg) = when (tone) {
        AslBannerTone.Info -> colors.infoContainer to colors.info
        AslBannerTone.Warning -> colors.warningContainer to colors.warning
        AslBannerTone.Error -> colors.errorContainer to colors.error
        AslBannerTone.Success -> colors.successContainer to colors.success
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .background(bg, AslShape.sm)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AslIcon(name = toneIcon(tone), size = 17.dp, tint = fg)
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = fg,
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
        if (onDismiss != null) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                AslIcon(name = "x", size = 15.dp, tint = colors.textTertiary)
            }
        }
    }
}
