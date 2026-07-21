package com.ahmadkharfan.androidstudiolite.designsystem.component.content

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDropdownMenu
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDropdownMenuDivider
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDropdownMenuItem
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslFileIcons
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslMetrics
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslMotion
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

enum class AslGitStatus(val letter: String) { Modified("M"), Added("A"), Deleted("D"), Untracked("?"), Conflicted("!") }

enum class AslFileTreeAction {
    NewFile, NewFolder, Rename, Copy, Paste, Delete, ShowHistory, Blame, AddToGitignore,
}

@Immutable
data class AslFileTreeNode(
    val id: String,
    val name: String,
    val children: List<AslFileTreeNode>? = null,
    val icon: String? = null,
    val git: AslGitStatus? = null,
)

@Immutable
private data class FlatTreeRow(
    val node: AslFileTreeNode,
    val depth: Int,
)

@Composable
fun AslFileTree(
    items: List<AslFileTreeNode>,
    expandedIds: Set<String>,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
    actionsEnabled: Boolean = false,
    canPaste: Boolean = false,
    selectDirectories: Boolean = false,
    onToggle: (String) -> Unit = {},
    onSelect: (AslFileTreeNode) -> Unit = {},
    onFocus: (AslFileTreeNode) -> Unit = {},
    onAction: (AslFileTreeNode, AslFileTreeAction) -> Unit = { _, _ -> },
) {
    val latestOnToggle by rememberUpdatedState(onToggle)
    val latestOnSelect by rememberUpdatedState(onSelect)
    val latestOnFocus by rememberUpdatedState(onFocus)
    val latestOnAction by rememberUpdatedState(onAction)
    val handleToggle = remember { { id: String -> latestOnToggle(id) } }
    val handleSelect = remember { { node: AslFileTreeNode -> latestOnSelect(node) } }
    val handleFocus = remember { { node: AslFileTreeNode -> latestOnFocus(node) } }
    val handleAction = remember { { node: AslFileTreeNode, action: AslFileTreeAction -> latestOnAction(node, action) } }

    val flatRows = remember(items, expandedIds) { flattenVisibleRows(items, expandedIds) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val viewportWidth = maxWidth
        val treeWidth = maxOf(viewportWidth, estimatedTreeWidth(items))
        Column(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .widthIn(min = treeWidth)
                .padding(vertical = 4.dp),
        ) {
            flatRows.forEach { row ->
                key(row.node.id) {
                    AslFileTreeRow(
                        node = row.node,
                        depth = row.depth,
                        expanded = row.node.id in expandedIds,
                        selected = row.node.id == selectedId,
                        rowWidth = treeWidth,
                        actionsEnabled = actionsEnabled,
                        canPaste = canPaste,
                        selectDirectories = selectDirectories,
                        onToggle = handleToggle,
                        onSelect = handleSelect,
                        onFocus = handleFocus,
                        onAction = handleAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun AslFileTreeRow(
    node: AslFileTreeNode,
    depth: Int,
    expanded: Boolean,
    selected: Boolean,
    rowWidth: Dp,
    actionsEnabled: Boolean,
    canPaste: Boolean,
    selectDirectories: Boolean,
    onToggle: (String) -> Unit,
    onSelect: (AslFileTreeNode) -> Unit,
    onFocus: (AslFileTreeNode) -> Unit,
    onAction: (AslFileTreeNode, AslFileTreeAction) -> Unit,
) {
    val colors = AslTheme.colors
    val isDir = node.children != null
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
            .then(
                if (!selectDirectories) {
                    Modifier.pointerInput(node.id, actionsEnabled) {
                        detectTapGestures(
                            onTap = {
                                if (isDir) {
                                    onToggle(node.id)
                                } else {
                                    onSelect(node)
                                }
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
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .width(rowWidth)
                .height(AslMetrics.treeRow)
                .padding(start = (8 + depth * 16).dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isDir) {
                val rotation by animateFloatAsState(
                    targetValue = if (expanded) 90f else 0f,
                    animationSpec = AslMotion.standardSpec(),
                    label = "chevronRotation",
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .then(
                            if (selectDirectories) {
                                Modifier.pointerInput(node.id) {
                                    detectTapGestures(onTap = { onToggle(node.id) })
                                }
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    AslIcon(
                        name = "chevron-right",
                        size = 14.dp,
                        tint = colors.textTertiary,
                        modifier = Modifier.rotate(rotation),
                    )
                }
            } else {
                Box(modifier = Modifier.width(14.dp))
            }
            Spacer(Modifier.width(6.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (selectDirectories) {
                            Modifier.pointerInput(node.id) {
                                detectTapGestures(onTap = {
                                    onFocus(node)
                                    onSelect(node)
                                })
                            }
                        } else {
                            Modifier
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AslIcon(
                    name = node.icon ?: if (isDir) (if (expanded) "folder-open" else "folder") else "file",
                    size = 16.dp,
                    tint = when {
                        isDir -> colors.textSecondary
                        else -> AslFileIcons.tintFor(node.name, colors)
                    },
                )
                Spacer(Modifier.width(6.dp))
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
                        style = AslCode.codeTiny,
                        color = gitTint(node.git, colors),
                    )
                }
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
}

@Composable
private fun AslFileTreeActionMenu(
    isDirectory: Boolean,
    canPaste: Boolean,
    openUpward: Boolean,
    onDismiss: () -> Unit,
    onSelect: (AslFileTreeAction) -> Unit,
) {
    val latestOnSelect by rememberUpdatedState(onSelect)
    val dismiss = remember(onDismiss) { onDismiss }
    AslDropdownMenu(
        expanded = true,
        onDismissRequest = dismiss,
        offset = if (openUpward) DpOffset(0.dp, (-8).dp) else DpOffset.Zero,
    ) {
        if (isDirectory) {
            AslDropdownMenuItem(label = "New file", icon = "file-plus-2", onClick = { latestOnSelect(AslFileTreeAction.NewFile) })
            AslDropdownMenuItem(label = "New folder", icon = "folder", onClick = { latestOnSelect(AslFileTreeAction.NewFolder) })
            if (canPaste) {
                AslDropdownMenuItem(label = "Paste", icon = "copy", onClick = { latestOnSelect(AslFileTreeAction.Paste) })
            }
            AslDropdownMenuDivider()
        }
        AslDropdownMenuItem(label = "Rename", icon = "pencil", onClick = { latestOnSelect(AslFileTreeAction.Rename) })
        AslDropdownMenuItem(label = "Copy", icon = "copy", onClick = { latestOnSelect(AslFileTreeAction.Copy) })
        AslDropdownMenuDivider()
        AslDropdownMenuItem(label = "Delete", icon = "trash-2", destructive = true, onClick = { latestOnSelect(AslFileTreeAction.Delete) })
    }
}

private fun flattenVisibleRows(
    items: List<AslFileTreeNode>,
    expandedIds: Set<String>,
    depth: Int = 0,
): List<FlatTreeRow> = buildList {
    items.forEach { node ->
        add(FlatTreeRow(node, depth))
        if (node.children != null && node.id in expandedIds) {
            addAll(flattenVisibleRows(node.children, expandedIds, depth + 1))
        }
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

private fun gitTint(status: AslGitStatus, colors: AslColorScheme): Color = when (status) {
    AslGitStatus.Modified -> colors.info
    AslGitStatus.Added -> colors.success
    AslGitStatus.Deleted -> colors.error
    AslGitStatus.Untracked -> colors.warning
    AslGitStatus.Conflicted -> colors.error
}
