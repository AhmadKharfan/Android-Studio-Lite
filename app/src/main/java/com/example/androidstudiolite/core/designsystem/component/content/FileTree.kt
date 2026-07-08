package com.example.androidstudiolite.core.designsystem.component.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.core.designsystem.icon.AslIcon
import com.example.androidstudiolite.core.designsystem.theme.AslMetrics
import com.example.androidstudiolite.core.designsystem.theme.AslShape
import com.example.androidstudiolite.core.designsystem.theme.AslTheme

enum class AslGitStatus(val letter: String) { Modified("M"), Added("A"), Deleted("D"), Untracked("?") }

data class AslFileTreeNode(
    val id: String,
    val name: String,
    val children: List<AslFileTreeNode>? = null,
    val icon: String? = null,
    val git: AslGitStatus? = null,
)

/** FileTree.jsx — 36dp rows, indent guides, expand chevrons, git-status tint. */
@Composable
fun AslFileTree(
    items: List<AslFileTreeNode>,
    expandedIds: Set<String>,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
    onToggle: (String) -> Unit = {},
    onSelect: (AslFileTreeNode) -> Unit = {},
) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        items.forEach { node ->
            AslFileTreeRow(
                node = node,
                depth = 0,
                expandedIds = expandedIds,
                selectedId = selectedId,
                onToggle = onToggle,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun AslFileTreeRow(
    node: AslFileTreeNode,
    depth: Int,
    expandedIds: Set<String>,
    selectedId: String?,
    onToggle: (String) -> Unit,
    onSelect: (AslFileTreeNode) -> Unit,
) {
    val colors = AslTheme.colors
    val isDir = node.children != null
    val expanded = node.id in expandedIds
    val selected = node.id == selectedId

    Box(
        modifier = Modifier
            .background(if (selected) colors.accentPrimaryContainer else Color.Transparent, AslShape.sm)
            .clickable { if (isDir) onToggle(node.id) else onSelect(node) },
    ) {
        Row(
            modifier = Modifier
                .height(AslMetrics.treeRow)
                .padding(start = (8 + depth * 16).dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isDir) {
                AslIcon(name = if (expanded) "chevron-down" else "chevron-right", size = 14.dp, tint = colors.textTertiary)
            } else {
                Box(modifier = Modifier.width(14.dp))
            }
            androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
            AslIcon(
                name = node.icon ?: if (isDir) (if (expanded) "folder-open" else "folder") else "file-code",
                size = 16.dp,
                tint = if (isDir) colors.textSecondary else colors.textTertiary,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = node.git?.let { gitTint(it, colors) } ?: colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (node.git != null) {
                Text(
                    text = node.git.letter,
                    style = com.example.androidstudiolite.core.designsystem.theme.AslCode.codeTiny,
                    color = gitTint(node.git, colors),
                )
            }
        }
    }
    if (isDir && expanded) {
        node.children!!.forEach { child ->
            AslFileTreeRow(
                node = child,
                depth = depth + 1,
                expandedIds = expandedIds,
                selectedId = selectedId,
                onToggle = onToggle,
                onSelect = onSelect,
            )
        }
    }
}

private fun gitTint(status: AslGitStatus, colors: com.example.androidstudiolite.core.designsystem.theme.AslColorScheme): Color = when (status) {
    AslGitStatus.Modified -> colors.info
    AslGitStatus.Added -> colors.success
    AslGitStatus.Deleted -> colors.error
    AslGitStatus.Untracked -> colors.warning
}
