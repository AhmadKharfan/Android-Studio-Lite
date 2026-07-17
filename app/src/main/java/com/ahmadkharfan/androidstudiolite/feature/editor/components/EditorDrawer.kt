package com.ahmadkharfan.androidstudiolite.feature.editor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.animation.AslStateCrossfade
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslIconButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslFileTreeAction
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslFileTree
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslFileTreeNode
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslGitStatus
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslSkeleton
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslSkeletonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslToolRail
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslToolRailEntry
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslToolWindowPanel
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslMotion
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffTarget
import com.ahmadkharfan.androidstudiolite.feature.editor.aichat.AiChatRoute
import com.ahmadkharfan.androidstudiolite.feature.editor.assets.AssetsRoute
import com.ahmadkharfan.androidstudiolite.feature.editor.git.GitPanelRoute
import com.ahmadkharfan.androidstudiolite.feature.editor.EditorFileCreateKind
import com.ahmadkharfan.androidstudiolite.feature.editor.EditorFileNodeUiModel
import com.ahmadkharfan.androidstudiolite.feature.editor.EditorFileTreeAction
import com.ahmadkharfan.androidstudiolite.feature.editor.EditorRailTool
import com.ahmadkharfan.androidstudiolite.feature.editor.variants.VariantsRoute

private fun railItems(gitBadge: String?) = listOf(
    AslToolRailEntry.Item("files", "folder", "Files"),
    AslToolRailEntry.Item("git", "git-branch", "Git", badge = gitBadge),
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
    projectId: String,
    gitBadge: String?,
    fileTree: List<EditorFileNodeUiModel>,
    expandedFolderIds: Set<String>,
    selectedFileId: String?,
    canPasteFileTreeEntry: Boolean,
    onSelectTool: (EditorRailTool) -> Unit,
    onFocusFileTreeNode: (String) -> Unit,
    onToggleFolder: (String) -> Unit,
    onSelectFile: (id: String, name: String) -> Unit,
    onCreateFileTreeEntry: (EditorFileCreateKind, String?) -> Unit,
    onFileTreeAction: (EditorFileTreeAction, id: String, name: String, isDirectory: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAiAgentSettings: () -> Unit,
    onOpenGitDiff: (String, GitDiffTarget) -> Unit,
    onOpenGitHistory: () -> Unit,
    onOpenGitBranches: () -> Unit,
    onOpenGitTags: () -> Unit,
    onOpenGitStashes: () -> Unit,
    onOpenGitConflicts: () -> Unit,
    onCloseProject: () -> Unit,
    modifier: Modifier = Modifier,
    isLoadingFileTree: Boolean = false,
) {
    val visible = openTool != null
    // Retain the last opened tool so the slide-out keeps rendering its content while it animates away.
    var lastTool by remember { mutableStateOf<EditorRailTool?>(null) }
    if (openTool != null) lastTool = openTool
    val tool = lastTool ?: return

    // Both transitions are seeded at `false` regardless of `visible`'s first value — a plain
    // `AnimatedVisibility(visible = ...)` initializes its MutableTransitionState AT that first value,
    // so if this composable's very first composition already has visible = true (the first time the
    // drawer is ever opened), there is nothing to animate *from* and it just snaps in. Forcing the
    // initial state to false makes the very first open play the same enter animation as every later one.
    val scrimState = remember { MutableTransitionState(false) }
    scrimState.targetState = visible
    val panelState = remember { MutableTransitionState(false) }
    panelState.targetState = visible

    Box(modifier = modifier.fillMaxSize()) {
        // Dim scrim fades in/out and only intercepts touches while the drawer is present.
        AnimatedVisibility(
            visibleState = scrimState,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(AslMotion.standardSpec()),
            exit = fadeOut(AslMotion.standardSpec()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(onClick = onDismiss),
            )
        }
        // Rail + tool panel slide in from the leading edge.
        AnimatedVisibility(
            visibleState = panelState,
            enter = slideInHorizontally(AslMotion.offsetSpec()) { -it } + fadeIn(AslMotion.enterSpec()),
            exit = slideOutHorizontally(AslMotion.offsetSpec(AslMotion.fast)) { -it } + fadeOut(AslMotion.exitSpec()),
        ) {
            Row(modifier = Modifier.fillMaxHeight()) {
                EditorToolRail(
                    activeId = tool.toRailId(),
                    gitBadge = gitBadge,
                    onSelectTool = onSelectTool,
                    onOpenSettings = onOpenSettings,
                    onCloseProject = onCloseProject,
                )
                EditorToolPanelContent(
                    openTool = tool,
                    projectId = projectId,
                    fileTree = fileTree,
                    expandedFolderIds = expandedFolderIds,
                    selectedFileId = selectedFileId,
                    canPasteFileTreeEntry = canPasteFileTreeEntry,
                    onFocusFileTreeNode = onFocusFileTreeNode,
                    onToggleFolder = onToggleFolder,
                    onSelectFile = onSelectFile,
                    onCreateFileTreeEntry = onCreateFileTreeEntry,
                    onFileTreeAction = onFileTreeAction,
                    onDismiss = onDismiss,
                    onOpenAiAgentSettings = onOpenAiAgentSettings,
                    onOpenGitDiff = onOpenGitDiff,
                    onOpenGitHistory = onOpenGitHistory,
                    onOpenGitBranches = onOpenGitBranches,
                    onOpenGitTags = onOpenGitTags,
                    onOpenGitStashes = onOpenGitStashes,
                    onOpenGitConflicts = onOpenGitConflicts,
                    isLoadingFileTree = isLoadingFileTree,
                )
            }
        }
    }
}

/** Tablet (>=600dp) docked variant: rail is always visible (no overlay/scrim), the panel sits
 *  inline in the layout instead of sliding over the editor — matches S12t's "ToolRail persistent,
 *  Project tool window docked (250dp)". Caller composes this beside the code-editor column. */
@Composable
fun EditorDockedPanel(
    openTool: EditorRailTool?,
    projectId: String,
    gitBadge: String?,
    fileTree: List<EditorFileNodeUiModel>,
    expandedFolderIds: Set<String>,
    selectedFileId: String?,
    canPasteFileTreeEntry: Boolean,
    onSelectTool: (EditorRailTool) -> Unit,
    onFocusFileTreeNode: (String) -> Unit,
    onToggleFolder: (String) -> Unit,
    onSelectFile: (id: String, name: String) -> Unit,
    onCreateFileTreeEntry: (EditorFileCreateKind, String?) -> Unit,
    onFileTreeAction: (EditorFileTreeAction, id: String, name: String, isDirectory: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAiAgentSettings: () -> Unit,
    onOpenGitDiff: (String, GitDiffTarget) -> Unit,
    onOpenGitHistory: () -> Unit,
    onOpenGitBranches: () -> Unit,
    onOpenGitTags: () -> Unit,
    onOpenGitStashes: () -> Unit,
    onOpenGitConflicts: () -> Unit,
    onCloseProject: () -> Unit,
    modifier: Modifier = Modifier,
    isLoadingFileTree: Boolean = false,
) {
    Row(modifier = modifier.fillMaxHeight()) {
        EditorToolRail(
            activeId = openTool.toRailId(),
            gitBadge = gitBadge,
            onSelectTool = onSelectTool,
            onOpenSettings = onOpenSettings,
            onCloseProject = onCloseProject,
        )
        if (openTool != null) {
            EditorToolPanelContent(
                openTool = openTool,
                projectId = projectId,
                fileTree = fileTree,
                expandedFolderIds = expandedFolderIds,
                selectedFileId = selectedFileId,
                canPasteFileTreeEntry = canPasteFileTreeEntry,
                onFocusFileTreeNode = onFocusFileTreeNode,
                onToggleFolder = onToggleFolder,
                onSelectFile = onSelectFile,
                onCreateFileTreeEntry = onCreateFileTreeEntry,
                onFileTreeAction = onFileTreeAction,
                onDismiss = onDismiss,
                onOpenAiAgentSettings = onOpenAiAgentSettings,
                onOpenGitDiff = onOpenGitDiff,
                onOpenGitHistory = onOpenGitHistory,
                onOpenGitBranches = onOpenGitBranches,
                onOpenGitTags = onOpenGitTags,
                onOpenGitStashes = onOpenGitStashes,
                onOpenGitConflicts = onOpenGitConflicts,
                isLoadingFileTree = isLoadingFileTree,
            )
        }
    }
}

@Composable
private fun EditorToolRail(
    activeId: String?,
    gitBadge: String?,
    onSelectTool: (EditorRailTool) -> Unit,
    onOpenSettings: () -> Unit,
    onCloseProject: () -> Unit,
) {
    AslToolRail(
        items = railItems(gitBadge),
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
    projectId: String,
    fileTree: List<EditorFileNodeUiModel>,
    expandedFolderIds: Set<String>,
    selectedFileId: String?,
    canPasteFileTreeEntry: Boolean,
    onFocusFileTreeNode: (String) -> Unit,
    onToggleFolder: (String) -> Unit,
    onSelectFile: (id: String, name: String) -> Unit,
    onCreateFileTreeEntry: (EditorFileCreateKind, String?) -> Unit,
    onFileTreeAction: (EditorFileTreeAction, id: String, name: String, isDirectory: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onOpenAiAgentSettings: () -> Unit,
    onOpenGitDiff: (String, GitDiffTarget) -> Unit,
    onOpenGitHistory: () -> Unit,
    onOpenGitBranches: () -> Unit,
    onOpenGitTags: () -> Unit,
    onOpenGitStashes: () -> Unit,
    onOpenGitConflicts: () -> Unit,
    isLoadingFileTree: Boolean = false,
) {
    // Cross-fade between Files/Git/AI Agent/Variants/Assets on rail switch, instead of an instant
    // swap; animateContentSize smooths the width change too (each tool panel has its own fixed width).
    AslStateCrossfade(
        targetState = openTool,
        modifier = Modifier.animateContentSize(AslMotion.standardSpec()),
        label = "toolPanel",
    ) { tool ->
        when (tool) {
            EditorRailTool.Files -> AslToolWindowPanel(
                title = "Project",
                // Wider than the original 252dp, but still leaves enough editor visible to avoid
                // feeling like a full-screen takeover on phones.
                width = minOf(
                    androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp * 0.72f,
                    320f,
                ).dp,
                onClose = onDismiss,
                actions = {
                    FileTreeCreateMenu(onCreate = { kind -> onCreateFileTreeEntry(kind, null) })
                },
            ) {
                AslStateCrossfade(targetState = isLoadingFileTree, label = "fileTreeLoading") { loading ->
                    if (loading) {
                        AslSkeleton(variant = AslSkeletonVariant.List, rows = 5)
                    } else {
                        AslFileTree(
                            items = fileTree.map { it.toAslNode() },
                            expandedIds = expandedFolderIds,
                            selectedId = selectedFileId,
                            actionsEnabled = true,
                            canPaste = canPasteFileTreeEntry,
                            onFocus = { onFocusFileTreeNode(it.id) },
                            onToggle = onToggleFolder,
                            onSelect = { onSelectFile(it.id, it.name) },
                            onAction = { node, action ->
                                onFileTreeAction(action.toEditorAction(), node.id, node.name, node.children != null)
                            },
                        )
                    }
                }
            }
            EditorRailTool.Git -> GitPanelRoute(
                projectId = projectId,
                onClose = onDismiss,
                onOpenDiff = onOpenGitDiff,
                onOpenHistory = onOpenGitHistory,
                onOpenBranches = onOpenGitBranches,
                onOpenTags = onOpenGitTags,
                onOpenStashes = onOpenGitStashes,
                onOpenConflicts = onOpenGitConflicts,
            )
            EditorRailTool.AiAgent -> AiChatRoute(onClose = onDismiss, onOpenAiAgentSettings = onOpenAiAgentSettings)
            EditorRailTool.Variants -> VariantsRoute(onClose = onDismiss)
            EditorRailTool.Assets -> AssetsRoute(onClose = onDismiss)
        }
    }
}

@Composable
private fun FileTreeCreateMenu(onCreate: (EditorFileCreateKind) -> Unit) {
    val colors = com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        AslIconButton(
            icon = "file-plus-2",
            contentDescription = "New file or folder",
            onClick = { open = !open },
            active = open,
            size = 32.dp,
            iconSize = 16.dp,
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.widthIn(min = 196.dp),
            containerColor = colors.surface,
            shadowElevation = 8.dp,
            tonalElevation = 0.dp,
            border = BorderStroke(1.dp, colors.borderStrong),
            shape = AslShape.lg,
        ) {
            DropdownMenuItem(
                text = { Text("New file", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary) },
                leadingIcon = {
                    com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon("file-plus-2", size = 16.dp, tint = colors.textSecondary)
                },
                onClick = {
                    open = false
                    onCreate(EditorFileCreateKind.File)
                },
            )
            DropdownMenuItem(
                text = { Text("New folder", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary) },
                leadingIcon = {
                    com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon("folder", size = 16.dp, tint = colors.textSecondary)
                },
                onClick = {
                    open = false
                    onCreate(EditorFileCreateKind.Folder)
                },
            )
        }
    }
}

private fun AslFileTreeAction.toEditorAction(): EditorFileTreeAction = when (this) {
    AslFileTreeAction.NewFile -> EditorFileTreeAction.NewFile
    AslFileTreeAction.NewFolder -> EditorFileTreeAction.NewFolder
    AslFileTreeAction.Rename -> EditorFileTreeAction.Rename
    AslFileTreeAction.Copy -> EditorFileTreeAction.Copy
    AslFileTreeAction.Paste -> EditorFileTreeAction.Paste
    AslFileTreeAction.Delete -> EditorFileTreeAction.Delete
    AslFileTreeAction.ShowHistory -> EditorFileTreeAction.ShowHistory
    AslFileTreeAction.Blame -> EditorFileTreeAction.Blame
    AslFileTreeAction.AddToGitignore -> EditorFileTreeAction.AddToGitignore
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
    GitFileStatus.CONFLICTED -> AslGitStatus.Conflicted
}
