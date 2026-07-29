package com.ahmadkharfan.androidstudiolite.designsystem.component.feedback

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.R
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.preview.PreviewAslComponent
import com.ahmadkharfan.androidstudiolite.designsystem.preview.AslPreview
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

enum class AslStatus { Building, Syncing, Indexing, Success, Failed }

private data class StatusSpec(@StringRes val labelRes: Int, val icon: String, val isBusy: Boolean)

private fun spec(status: AslStatus): StatusSpec = when (status) {
    AslStatus.Building -> StatusSpec(R.string.asl_status_building, "hammer", isBusy = true)
    AslStatus.Syncing -> StatusSpec(R.string.asl_status_syncing, "refresh-cw", isBusy = true)
    AslStatus.Indexing -> StatusSpec(R.string.asl_status_indexing, "database", isBusy = true)
    AslStatus.Success -> StatusSpec(R.string.asl_status_success, "check", isBusy = false)
    AslStatus.Failed -> StatusSpec(R.string.asl_status_failed, "x", isBusy = false)
}

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
            .heightIn(min = 26.dp)
            .background(bg, AslShape.full)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (s.isBusy) {
            AslCircularProgress(size = 12.dp, thickness = 2.dp, color = fg)
        } else {
            AslIcon(name = s.icon, size = 13.dp, tint = fg)
        }
        Text(
            text = label ?: stringResource(s.labelRes),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@PreviewAslComponent
@Composable
private fun AslStatusChipPreview() {
    AslPreview {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AslStatusChip(status = AslStatus.Building)
            AslStatusChip(status = AslStatus.Syncing)
            AslStatusChip(status = AslStatus.Indexing)
            AslStatusChip(status = AslStatus.Success)
            AslStatusChip(status = AslStatus.Failed)
        }
    }
}
