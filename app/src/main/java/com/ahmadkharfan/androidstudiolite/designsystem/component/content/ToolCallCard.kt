package com.ahmadkharfan.androidstudiolite.designsystem.component.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonSize
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

enum class AslToolCallState { Pending, Running, Done, Failed, Rejected }

/**
 * A single agent file operation surfaced in the chat: an icon + summary, a status chip, an optional
 * diff preview, and — when [state] is [AslToolCallState.Pending] — Approve/Reject actions.
 */
@Composable
fun AslToolCallCard(
    title: String,
    icon: String,
    state: AslToolCallState,
    modifier: Modifier = Modifier,
    diffOld: String? = null,
    diffNew: String? = null,
    result: String? = null,
    approveLabel: String = "Approve",
    rejectLabel: String = "Reject",
    onApprove: () -> Unit = {},
    onReject: () -> Unit = {},
) {
    val colors = AslTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AslShape.md)
            .background(colors.surface, AslShape.md)
            .border(1.dp, colors.borderDefault, AslShape.md)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AslIcon(name = icon, size = 16.dp, tint = colors.textSecondary)
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            ToolStatusChip(state = state)
        }

        if (diffNew != null) {
            DiffPreview(diffOld = diffOld, diffNew = diffNew)
        }

        if (!result.isNullOrBlank() && state != AslToolCallState.Pending) {
            Text(
                text = result.trim(),
                style = AslCode.codeTiny,
                color = colors.textTertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 120.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }

        if (state == AslToolCallState.Pending) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AslButton(
                    label = approveLabel,
                    icon = "check",
                    onClick = onApprove,
                    variant = AslButtonVariant.Primary,
                    size = AslButtonSize.Md,
                    modifier = Modifier.weight(1f),
                )
                AslButton(
                    label = rejectLabel,
                    icon = "x",
                    onClick = onReject,
                    variant = AslButtonVariant.Secondary,
                    size = AslButtonSize.Md,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ToolStatusChip(state: AslToolCallState) {
    val colors = AslTheme.colors
    val (label, fg, bg) = when (state) {
        AslToolCallState.Pending -> Triple("Review", colors.warning, colors.warningContainer)
        AslToolCallState.Running -> Triple("Running", colors.info, colors.infoContainer)
        AslToolCallState.Done -> Triple("Done", colors.success, colors.successContainer)
        AslToolCallState.Failed -> Triple("Failed", colors.error, colors.errorContainer)
        AslToolCallState.Rejected -> Triple("Rejected", colors.textTertiary, colors.surfaceContainerHigh)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        modifier = Modifier
            .clip(AslShape.full)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun DiffPreview(diffOld: String?, diffNew: String) {
    val colors = AslTheme.colors
    val lines = remember(diffOld, diffNew) { computeLineDiff(diffOld, diffNew) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AslShape.sm)
            .background(colors.editorCanvas, AslShape.sm)
            .border(1.dp, colors.borderSubtle, AslShape.sm)
            .heightIn(max = 240.dp)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
    ) {
        val horizontal = rememberScrollState()
        Column(modifier = Modifier.horizontalScroll(horizontal)) {
            lines.forEach { line ->
                val color = when (line.kind) {
                    DiffKind.Added -> colors.success
                    DiffKind.Removed -> colors.error
                    DiffKind.Context -> colors.textSecondary
                }
                Text(
                    text = "${line.kind.marker}${line.text}",
                    style = AslCode.codeTiny,
                    color = color,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            }
        }
    }
}

private enum class DiffKind(val marker: String) { Added("+ "), Removed("- "), Context("  ") }

private data class DiffLine(val kind: DiffKind, val text: String)

/** A compact LCS line diff. When [old] is null every new line is an addition (a freshly created file). */
private fun computeLineDiff(old: String?, new: String): List<DiffLine> {
    val newLines = new.split("\n")
    if (old == null) {
        return newLines.take(MAX_DIFF_LINES).map { DiffLine(DiffKind.Added, it) }
    }
    val oldLines = old.split("\n")
    val m = oldLines.size
    val n = newLines.size
    val lcs = Array(m + 1) { IntArray(n + 1) }
    for (i in m - 1 downTo 0) {
        for (j in n - 1 downTo 0) {
            lcs[i][j] = if (oldLines[i] == newLines[j]) {
                lcs[i + 1][j + 1] + 1
            } else {
                maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }
    }
    val result = ArrayList<DiffLine>()
    var i = 0
    var j = 0
    while (i < m && j < n && result.size < MAX_DIFF_LINES) {
        when {
            oldLines[i] == newLines[j] -> {
                result.add(DiffLine(DiffKind.Context, oldLines[i])); i++; j++
            }
            lcs[i + 1][j] >= lcs[i][j + 1] -> {
                result.add(DiffLine(DiffKind.Removed, oldLines[i])); i++
            }
            else -> {
                result.add(DiffLine(DiffKind.Added, newLines[j])); j++
            }
        }
    }
    while (i < m && result.size < MAX_DIFF_LINES) result.add(DiffLine(DiffKind.Removed, oldLines[i++]))
    while (j < n && result.size < MAX_DIFF_LINES) result.add(DiffLine(DiffKind.Added, newLines[j++]))
    return result
}

private const val MAX_DIFF_LINES = 400
