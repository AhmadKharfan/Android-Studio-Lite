package com.example.androidstudiolite.feature.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.core.designsystem.component.buttons.AslIconButton
import com.example.androidstudiolite.core.designsystem.component.content.AslFileTree
import com.example.androidstudiolite.core.designsystem.component.content.AslFileTreeNode
import com.example.androidstudiolite.core.designsystem.component.content.AslGitStatus
import com.example.androidstudiolite.core.designsystem.component.feedback.AslSkeleton
import com.example.androidstudiolite.core.designsystem.component.feedback.AslSkeletonVariant
import com.example.androidstudiolite.core.designsystem.component.navigation.AslToolRail
import com.example.androidstudiolite.core.designsystem.component.navigation.AslToolRailEntry
import com.example.androidstudiolite.core.designsystem.component.navigation.AslToolWindowPanel
import com.example.androidstudiolite.domain.model.GitFileStatus
import com.example.androidstudiolite.feature.editor.aichat.screen.AiChatRoute
import com.example.androidstudiolite.feature.editor.assets.screen.AssetsRoute
import com.example.androidstudiolite.feature.editor.git.screen.GitPanelRoute
import com.example.androidstudiolite.feature.editor.uiState.EditorFileNodeUiModel
import com.example.androidstudiolite.feature.editor.uiState.EditorRailTool
import com.example.androidstudiolite.feature.editor.variants.screen.VariantsRoute

private val RAIL_ITEMS = listOf(
    AslToolRailEntry.Item("files", "folder", "Files"),
    AslToolRailEntry.Item("git", "git-branch", "Git", badge = "4"),
    AslToolRailEntry.Item("ai", "sparkles", "AI Agent"),
    AslToolRailEntry.Item("variants", "layers", "Variants"),
    AslToolRailEntry.Item("assets", "image", "Assets"),
    AslToolRailEntry.Spacer,
    AslToolRailEntry.Divider,
    AslToolRailEntry.Item("settings", "settings", "Settings"),
    AslToolRailEntry.Item("close", "x", "Close project"),
)

private fun EditorRailTool?.toRailId(): String? = when (this) {
    EditorRailTool.Files -> "files"
    EditorRailTool.Git -> "git"
    EditorRailTool.AiAgent -> "ai"
    EditorRailTool.Variants -> "variants"
    EditorRailTool.Assets -> "assets"
    null -> null
}

private fun String.toRailTool(): EditorRailTool? = when (this) {
    "files" -> EditorRailTool.Files
    "git" -> EditorRailTool.Git
    "ai" -> EditorRailTool.AiAgent
    "variants" -> EditorRailTool.Variants
    "assets" -> EditorRailTool.Assets
    else -> null
}

/** Tool-window rail + its slide-out drawer content (phone, <600dp: overlay with dim scrim).
 *  Git/AI Agent/Variants/Assets have no dedicated design-spec screen card (Phase 3b was never
 *  provided), so their tool windows are composed from the shared component library per the
 *  design system's own composition map (README.md: "Git tool window: ToolWindowPanel · NavRail ·
 *  DiffLine · ListItem · TextField · StatusChip", "AI Agent: ToolWindowPanel · ChatBubble ·
 *  ChatCodeBlock · TextField · ApiKeyCard"). */
@Composable
fun EditorDrawer(
    openTool: EditorRailTool?,
    fileTree: List<EditorFileNodeUiModel>,
    expandedFolderIds: Set<String>,
    selectedFileId: String?,
    onSelectTool: (EditorRailTool) -> Unit,
    onToggleFolder: (String) -> Unit,
    onSelectFile: (id: String, name: String) -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAiAgentSettings: () -> Unit,
    onCloseProject: () -> Unit,
    modifier: Modifier = Modifier,
    isLoadingFileTree: Boolean = false,
) {
    if (openTool == null) return
    Row(modifier = modifier.fillMaxSize()) {
        EditorToolRail(
            activeId = openTool.toRailId(),
            onSelectTool = onSelectTool,
            onOpenSettings = onOpenSettings,
            onCloseProject = onCloseProject,
        )
        EditorToolPanelContent(
            openTool = openTool,
            fileTree = fileTree,
            expandedFolderIds = expandedFolderIds,
            selectedFileId = selectedFileId,
            onToggleFolder = onToggleFolder,
            onSelectFile = onSelectFile,
            onDismiss = onDismiss,
            onOpenAiAgentSettings = onOpenAiAgentSettings,
            isLoadingFileTree = isLoadingFileTree,
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(onClick = onDismiss),
        )
    }
}

/** Tablet (>=600dp) docked variant: rail is always visible (no overlay/scrim), the panel sits
 *  inline in the layout instead of sliding over the editor — matches S12t's "ToolRail persistent,
 *  Project tool window docked (250dp)". Caller composes this beside the code-editor column. */
@Composable
fun EditorDockedPanel(
    openTool: EditorRailTool?,
    fileTree: List<EditorFileNodeUiModel>,
    expandedFolderIds: Set<String>,
    selectedFileId: String?,
    onSelectTool: (EditorRailTool) -> Unit,
    onToggleFolder: (String) -> Unit,
    onSelectFile: (id: String, name: String) -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAiAgentSettings: () -> Unit,
    onCloseProject: () -> Unit,
    modifier: Modifier = Modifier,
    isLoadingFileTree: Boolean = false,
) {
    Row(modifier = modifier.fillMaxHeight()) {
        EditorToolRail(
            activeId = openTool.toRailId(),
            onSelectTool = onSelectTool,
            onOpenSettings = onOpenSettings,
            onCloseProject = onCloseProject,
        )
        if (openTool != null) {
            EditorToolPanelContent(
                openTool = openTool,
                fileTree = fileTree,
                expandedFolderIds = expandedFolderIds,
                selectedFileId = selectedFileId,
                onToggleFolder = onToggleFolder,
                onSelectFile = onSelectFile,
                onDismiss = onDismiss,
                onOpenAiAgentSettings = onOpenAiAgentSettings,
                isLoadingFileTree = isLoadingFileTree,
            )
        }
    }
}

@Composable
private fun EditorToolRail(
    activeId: String?,
    onSelectTool: (EditorRailTool) -> Unit,
    onOpenSettings: () -> Unit,
    onCloseProject: () -> Unit,
) {
    AslToolRail(
        items = RAIL_ITEMS,
        activeId = activeId,
        onSelect = { id ->
            when (id) {
                "settings" -> onOpenSettings()
                "close" -> onCloseProject()
                else -> id.toRailTool()?.let(onSelectTool)
            }
        },
    )
}

@Composable
private fun EditorToolPanelContent(
    openTool: EditorRailTool,
    fileTree: List<EditorFileNodeUiModel>,
    expandedFolderIds: Set<String>,
    selectedFileId: String?,
    onToggleFolder: (String) -> Unit,
    onSelectFile: (id: String, name: String) -> Unit,
    onDismiss: () -> Unit,
    onOpenAiAgentSettings: () -> Unit,
    isLoadingFileTree: Boolean = false,
) {
    when (openTool) {
        EditorRailTool.Files -> AslToolWindowPanel(
            title = "Project",
            width = 252.dp,
            onClose = onDismiss,
            actions = {
                AslIconButton(icon = "file-plus-2", contentDescription = "New file", onClick = {}, size = 32.dp, iconSize = 16.dp)
            },
        ) {
            if (isLoadingFileTree) {
                AslSkeleton(variant = AslSkeletonVariant.List, rows = 5)
            } else {
                AslFileTree(
                    items = fileTree.map { it.toAslNode() },
                    expandedIds = expandedFolderIds,
                    selectedId = selectedFileId,
                    onToggle = onToggleFolder,
                    onSelect = { onSelectFile(it.id, it.name) },
                )
            }
        }
        EditorRailTool.Git -> GitPanelRoute(onClose = onDismiss)
        EditorRailTool.AiAgent -> AiChatRoute(onClose = onDismiss, onOpenAiAgentSettings = onOpenAiAgentSettings)
        EditorRailTool.Variants -> VariantsRoute(onClose = onDismiss)
        EditorRailTool.Assets -> AssetsRoute(onClose = onDismiss)
    }
}

private fun EditorFileNodeUiModel.toAslNode(): AslFileTreeNode = AslFileTreeNode(
    id = id,
    name = name,
    children = children?.map { it.toAslNode() },
    icon = icon,
    git = gitStatus?.toAslGitStatus(),
)

private fun GitFileStatus.toAslGitStatus(): AslGitStatus = when (this) {
    GitFileStatus.MODIFIED -> AslGitStatus.Modified
    GitFileStatus.ADDED -> AslGitStatus.Added
    GitFileStatus.DELETED -> AslGitStatus.Deleted
    GitFileStatus.UNTRACKED -> AslGitStatus.Untracked
}
