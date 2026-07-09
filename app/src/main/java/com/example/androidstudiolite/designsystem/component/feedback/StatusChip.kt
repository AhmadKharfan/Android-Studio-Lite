package com.example.androidstudiolite.designsystem.component.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.designsystem.icon.AslIcon
import com.example.androidstudiolite.designsystem.theme.AslShape
import com.example.androidstudiolite.designsystem.theme.AslTheme

enum class AslStatus { Building, Syncing, Indexing, Success, Failed }

private data class StatusSpec(val label: String, val icon: String, val busy: Boolean)

private fun spec(status: AslStatus): StatusSpec = when (status) {
    AslStatus.Building -> StatusSpec("Building", "hammer", busy = true)
    AslStatus.Syncing -> StatusSpec("Syncing", "refresh-cw", busy = true)
    AslStatus.Indexing -> StatusSpec("Indexing", "database", busy = true)
    AslStatus.Success -> StatusSpec("Success", "check", busy = false)
    AslStatus.Failed -> StatusSpec("Failed", "x", busy = false)
}

/** StatusChip.jsx — live-state chip: building/syncing/indexing (spinner) · success/failed (icon). */
@Composable
fun AslStatusChip(
    status: AslStatus = AslStatus.Success,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val colors = AslTheme.colors
    val s = spec(status)
    val (bg, fg) = when (status) {
        AslStatus.Building, AslStatus.Syncing -> colors.infoContainer to colors.info
        AslStatus.Indexing -> colors.warningContainer to colors.warning
        AslStatus.Success -> colors.successContainer to colors.success
        AslStatus.Failed -> colors.errorContainer to colors.error
    }

    Row(
        modifier = modifier
            .height(26.dp)
            .background(bg, AslShape.full)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (s.busy) {
            AslCircularProgress(size = 12.dp, thickness = 2.dp, color = fg)
        } else {
            AslIcon(name = s.icon, size = 13.dp, tint = fg)
        }
        Text(
            text = label ?: s.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = fg,
        )
    }
}
