package com.ahmadkharfan.androidstudiolite.designsystem.component.content

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.PopupProperties
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslMetrics
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslMotion
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

enum class AslGitStatus(val letter: String) { Modified("M"), Added("A"), Deleted("D"), Untracked("?") }

enum class AslFileTreeAction { NewFile, NewFolder, Rename, Copy, Paste, Delete }

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
    actionsEnabled: Boolean = false,
    canPaste: Boolean = false,
    onToggle: (String) -> Unit = {},
    onSelect: (AslFileTreeNode) -> Unit = {},
    onFocus: (AslFileTreeNode) -> Unit = {},
    onAction: (AslFileTreeNode, AslFileTreeAction) -> Unit = { _, _ -> },
) {
    // Horizontal scroll so deeply-indented rows and long file names are fully readable instead of
    // being ellipsized. Each row is at least as wide as the visible panel, so clicking anywhere on
    // the row selects/toggles it; long rows can still grow beyond that and pan horizontally.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val viewportWidth = maxWidth
        val treeWidth = maxOf(viewportWidth, estimatedTreeWidth(items))
        Column(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .widthIn(min = treeWidth)
                .padding(vertical = 4.dp),
        ) {
            items.forEach { node ->
                AslFileTreeRow(
                    node = node,
                    depth = 0,
                    expandedIds = expandedIds,
                    selectedId = selectedId,
                    rowWidth = treeWidth,
                    actionsEnabled = actionsEnabled,
                    canPaste = canPaste,
                    onToggle = onToggle,
                    onSelect = onSelect,
                    onFocus = onFocus,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun AslFileTreeRow(
    node: AslFileTreeNode,
    depth: Int,
    expandedIds: Set<String>,
    selectedId: String?,
    rowWidth: Dp,
    actionsEnabled: Boolean,
    canPaste: Boolean,
    onToggle: (String) -> Unit,
    onSelect: (AslFileTreeNode) -> Unit,
    onFocus: (AslFileTreeNode) -> Unit,
    onAction: (AslFileTreeNode, AslFileTreeAction) -> Unit,
) {
    val colors = AslTheme.colors
    val isDir = node.children != null
    val expanded = node.id in expandedIds
    val selected = node.id == selectedId
    val density = LocalDensity.current
    var menuOpen by remember { mutableStateOf(false) }
    var menuAnchor by remember { mutableStateOf(DpOffset.Zero) }
    var openMenuUpward by remember { mutableStateOf(false) }
    val estimatedMenuHeightPx = with(density) {
        (if (isDir) {
            if (canPaste) 292.dp else 244.dp
        } else {
            148.dp
        }).toPx()
    }

    Box(
        modifier = Modifier
            .width(rowWidth)
            .background(if (selected) colors.accentPrimaryContainer else Color.Transparent, AslShape.sm)
            .pointerInput(node.id, actionsEnabled) {
                detectTapGestures(
                    onTap = {
                        onFocus(node)
                        if (isDir) onToggle(node.id) else onSelect(node)
                    },
                    onLongPress = if (actionsEnabled) {
                        { position ->
                            openMenuUpward = position.y < estimatedMenuHeightPx
                            menuAnchor = with(density) {
                                DpOffset(position.x.toDp(), position.y.toDp())
                            }
                            onFocus(node)
                            menuOpen = true
                        }
                    } else {
                        null
                    },
                )
            },
    ) {
        Row(
            modifier = Modifier
                .width(rowWidth)
                .height(AslMetrics.treeRow)
                .padding(start = (8 + depth * 16).dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isDir) {
                // Rotate a single chevron rather than swapping icons, so the toggle reads as one
                // continuous turn instead of a snap between two unrelated glyphs.
                val rotation by animateFloatAsState(
                    targetValue = if (expanded) 90f else 0f,
                    animationSpec = AslMotion.standardSpec(),
                    label = "chevronRotation",
                )
                AslIcon(
                    name = "chevron-right",
                    size = 14.dp,
                    tint = colors.textTertiary,
                    modifier = Modifier.rotate(rotation),
                )
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
            // No weight/ellipsis: the name lays out at full width on one line so the whole name is
            // visible when the tree is scrolled horizontally.
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = node.git?.let { gitTint(it, colors) } ?: colors.textPrimary,
                maxLines = 1,
                softWrap = false,
            )
            if (node.git != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = node.git.letter,
                    style = com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode.codeTiny,
                    color = gitTint(node.git, colors),
                )
            }
        }
        if (actionsEnabled && menuOpen) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = menuAnchor.x, y = menuAnchor.y)
                    .size(1.dp),
            ) {
                AslFileTreeActionMenu(
                    isDirectory = isDir,
                    canPaste = canPaste,
                    openUpward = openMenuUpward,
                    onDismiss = { menuOpen = false },
                    onSelect = { action ->
                        menuOpen = false
                        onAction(node, action)
                    },
                )
            }
        }
    }
    if (isDir) {
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(AslMotion.enterSpec()) + fadeIn(AslMotion.enterSpec()),
            exit = shrinkVertically(AslMotion.exitSpec()) + fadeOut(AslMotion.exitSpec()),
        ) {
            Column {
                node.children!!.forEach { child ->
                    AslFileTreeRow(
                        node = child,
                        depth = depth + 1,
                        expandedIds = expandedIds,
                        selectedId = selectedId,
                        rowWidth = rowWidth,
                        actionsEnabled = actionsEnabled,
                        canPaste = canPaste,
                        onToggle = onToggle,
                        onSelect = onSelect,
                        onFocus = onFocus,
                        onAction = onAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun AslFileTreeActionMenu(
    isDirectory: Boolean,
    canPaste: Boolean,
    openUpward: Boolean,
    onDismiss: () -> Unit,
    onSelect: (AslFileTreeAction) -> Unit,
) {
    val colors = AslTheme.colors
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 196.dp),
        offset = if (openUpward) {
            DpOffset(0.dp, (-8).dp)
        } else {
            DpOffset.Zero
        },
        containerColor = colors.surface,
        shadowElevation = 8.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, colors.borderStrong),
        shape = AslShape.lg,
        properties = PopupProperties(focusable = true),
    ) {
        if (isDirectory) {
            FileTreeMenuItem("New file", "file-plus-2") { onSelect(AslFileTreeAction.NewFile) }
            FileTreeMenuItem("New folder", "folder") { onSelect(AslFileTreeAction.NewFolder) }
            if (canPaste) {
                FileTreeMenuItem("Paste", "copy") { onSelect(AslFileTreeAction.Paste) }
            }
            HorizontalDivider()
        }
        FileTreeMenuItem("Rename", "pencil") { onSelect(AslFileTreeAction.Rename) }
        FileTreeMenuItem("Copy", "copy") { onSelect(AslFileTreeAction.Copy) }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Delete", style = MaterialTheme.typography.bodyMedium, color = colors.error) },
            leadingIcon = { AslIcon("trash-2", size = 16.dp, tint = colors.error) },
            onClick = { onSelect(AslFileTreeAction.Delete) },
        )
    }
}

private fun estimatedTreeWidth(nodes: List<AslFileTreeNode>): Dp =
    estimateTreeWidth(nodes, depth = 0).coerceAtLeast(0.dp)

private fun estimateTreeWidth(nodes: List<AslFileTreeNode>, depth: Int): Dp {
    var widest = 0.dp
    nodes.forEach { node ->
        val gitWidth = if (node.git != null) 24.dp else 0.dp
        val rowWidth = (8 + depth * 16).dp +
            14.dp +
            6.dp +
            16.dp +
            6.dp +
            (node.name.length * 10).dp +
            gitWidth +
            96.dp
        widest = maxOf(widest, rowWidth)
        node.children?.let { widest = maxOf(widest, estimateTreeWidth(it, depth + 1)) }
    }
    return widest
}

@Composable
private fun FileTreeMenuItem(label: String, icon: String, onClick: () -> Unit) {
    val colors = AslTheme.colors
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary) },
        leadingIcon = { AslIcon(icon, size = 16.dp, tint = colors.textSecondary) },
        onClick = onClick,
    )
}

private fun gitTint(status: AslGitStatus, colors: com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme): Color = when (status) {
    AslGitStatus.Modified -> colors.info
    AslGitStatus.Added -> colors.success
    AslGitStatus.Deleted -> colors.error
    AslGitStatus.Untracked -> colors.warning
}
