package com.ahmadkharfan.androidstudiolite.feature.editor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.animation.AslStateCrossfade
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslIconButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslFileTreeAction
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslFileTree
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslFileTreeNode
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslGitStatus
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDropdownMenu
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDropdownMenuItem
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslSkeleton
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslSkeletonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslToolRail
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslToolRailEntry
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslToolWindowPanel
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.rememberAslToolWindowWidth
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslMotion
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffTarget
import com.ahmadkharfan.androidstudiolite.feature.editor.aichat.AiChatRoute
import com.ahmadkharfan.androidstudiolite.feature.editor.assets.AssetsRoute
import com.ahmadkharfan.androidstudiolite.feature.git.api.GitPanelApi
import com.ahmadkharfan.androidstudiolite.feature.editor.EditorFileCreateKind
import com.ahmadkharfan.androidstudiolite.feature.editor.EditorFileNodeUiModel
import com.ahmadkharfan.androidstudiolite.feature.editor.EditorFileTreeAction
import com.ahmadkharfan.androidstudiolite.feature.editor.EditorRailTool
import com.ahmadkharfan.androidstudiolite.feature.editor.filetree.FileTreeSearchPanel
import com.ahmadkharfan.androidstudiolite.feature.editor.variants.VariantsRoute
import org.koin.compose.koinInject
import com.ahmadkharfan.androidstudiolite.feature.editor.R

@Composable
private fun railItems(gitBadge: String?) = listOf(
    AslToolRailEntry.Item("files", "folder", stringResource(R.string.editor_files)),
    AslToolRailEntry.Item("git", "git-branch", stringResource(R.string.editor_git), badge = gitBadge),
    AslToolRailEntry.Item("ai", "sparkles", stringResource(R.string.editor_ai_agent)),
    AslToolRailEntry.Item("variants", "layers", stringResource(R.string.editor_variants)),
    AslToolRailEntry.Item("assets", "image", stringResource(R.string.editor_assets)),
    AslToolRailEntry.Spacer,
    AslToolRailEntry.Divider,
    AslToolRailEntry.Item("settings", "settings", stringResource(R.string.editor_settings)),
    AslToolRailEntry.Item("close", "x", stringResource(R.string.editor_close_project)),
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

@Immutable
internal data class EditorDrawerState(
    val openTool: EditorRailTool?,
    val projectId: String,
    val gitBadge: String?,
    val fileTree: List<EditorFileNodeUiModel>,
    val expandedFolderIds: Set<String>,
    val selectedFileId: String?,
    val canPasteFileTreeEntry: Boolean,
    val selectedVariant: String,
    val availableVariants: List<String> = listOf("debug", "release"),
    val runModulePath: String = ":app",
    val isLoadingFileTree: Boolean = false,
)

internal data class EditorDrawerCallbacks(
    val onSelectTool: (EditorRailTool) -> Unit,
    val onFocusFileTreeNode: (String) -> Unit,
    val onToggleFolder: (String) -> Unit,
    val onSelectFile: (String, String) -> Unit,
    val onRevealFileTreeNode: (String) -> Unit,
    val onCreateFileTreeEntry: (EditorFileCreateKind, String?) -> Unit,
    val onFileTreeAction: (EditorFileTreeAction, String, String, Boolean) -> Unit,
    val onDismiss: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenAiAgentSettings: () -> Unit,
    val onCloseProject: () -> Unit,
    val onSelectVariant: (String) -> Unit,
)

internal data class GitNavigationCallbacks(
    val openDiff: (String, GitDiffTarget) -> Unit,
    val openFileHistory: (String) -> Unit,
    val openBlame: (String) -> Unit,
    val openBranches: () -> Unit,
    val openTags: () -> Unit,
    val openStashes: () -> Unit,
    val openHistory: () -> Unit,
    val openConflicts: () -> Unit,
)

@Composable
internal fun EditorDrawer(
    state: EditorDrawerState,
    callbacks: EditorDrawerCallbacks,
    gitNavigation: GitNavigationCallbacks,
    modifier: Modifier = Modifier,
) {
    val openTool = state.openTool
    val onDismiss = callbacks.onDismiss
    val visible = openTool != null

    var lastTool by remember { mutableStateOf<EditorRailTool?>(null) }
    if (openTool != null) lastTool = openTool
    val tool = lastTool ?: return


    val scrimState = remember { MutableTransitionState(false) }
    scrimState.targetState = visible
    val panelState = remember { MutableTransitionState(false) }
    panelState.targetState = visible

    Box(modifier = modifier.fillMaxSize()) {


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
        AnimatedVisibility(
            visibleState = panelState,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = slideInHorizontally(AslMotion.offsetSpec()) { -it } + fadeIn(AslMotion.enterSpec()),
            exit = slideOutHorizontally(AslMotion.offsetSpec(AslMotion.fast)) { -it } + fadeOut(AslMotion.exitSpec()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                EditorToolRail(activeId = tool.toRailId(), state = state, callbacks = callbacks)
                EditorToolPanelContent(
                    openTool = tool,
                    state = state,
                    callbacks = callbacks,
                    gitNavigation = gitNavigation,
                )
            }
        }
    }
}

@Composable
internal fun EditorDockedPanel(
    state: EditorDrawerState,
    callbacks: EditorDrawerCallbacks,
    gitNavigation: GitNavigationCallbacks,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxHeight()) {
        EditorToolRail(activeId = state.openTool.toRailId(), state = state, callbacks = callbacks)
        if (state.openTool != null) {
            EditorToolPanelContent(
                openTool = state.openTool,
                state = state,
                callbacks = callbacks,
                gitNavigation = gitNavigation,
            )
        }
    }
}

@Composable
private fun EditorToolRail(
    activeId: String?,
    state: EditorDrawerState,
    callbacks: EditorDrawerCallbacks,
) {
    val items = railItems(state.gitBadge)
    AslToolRail(
        items = items,
        activeId = activeId,
        onSelect = { id ->
            when (id) {
                "settings" -> callbacks.onOpenSettings()
                "close" -> callbacks.onCloseProject()
                else -> id.toRailTool()?.let(callbacks.onSelectTool)
            }
        },
    )
}

@Composable
private fun EditorToolPanelContent(
    openTool: EditorRailTool,
    state: EditorDrawerState,
    callbacks: EditorDrawerCallbacks,
    gitNavigation: GitNavigationCallbacks,
) {
    val gitPanelApi: GitPanelApi = koinInject()

    when (openTool) {
        EditorRailTool.Files -> EditorFilesToolPanel(
            state = state,
            callbacks = callbacks,
        )
        EditorRailTool.Git -> gitPanelApi.Panel(
            projectId = state.projectId,
            onClose = callbacks.onDismiss,
            onOpenDiff = gitNavigation.openDiff,
            onOpenHistory = gitNavigation.openHistory,
            onOpenBranches = gitNavigation.openBranches,
            onOpenTags = gitNavigation.openTags,
            onOpenStashes = gitNavigation.openStashes,
            onOpenConflicts = gitNavigation.openConflicts,
        )
        EditorRailTool.AiAgent -> AiChatRoute(
            projectId = state.projectId,
            onClose = callbacks.onDismiss,
            onOpenAiAgentSettings = callbacks.onOpenAiAgentSettings,
            activeFilePath = state.selectedFileId,
        )
        EditorRailTool.Variants -> VariantsRoute(
            selectedVariant = state.selectedVariant,
            onSelectVariant = callbacks.onSelectVariant,
            onClose = callbacks.onDismiss,
            module = state.runModulePath.removePrefix(":").ifBlank { "app" },
            variants = state.availableVariants,
        )
        EditorRailTool.Assets -> AssetsRoute(
            projectId = state.projectId,
            onClose = callbacks.onDismiss,
            onOpenFile = callbacks.onSelectFile,
        )
    }
}

@Composable
private fun EditorFilesToolPanel(
    state: EditorDrawerState,
    callbacks: EditorDrawerCallbacks,
) {
    val treeItems = remember(state.fileTree) { state.fileTree.map { it.toAslNode() } }
    val latestOnFocus by rememberUpdatedState(callbacks.onFocusFileTreeNode)
    val latestOnToggle by rememberUpdatedState(callbacks.onToggleFolder)
    val latestOnSelectFile by rememberUpdatedState(callbacks.onSelectFile)
    val latestOnFileTreeAction by rememberUpdatedState(callbacks.onFileTreeAction)
    val latestOnCreate by rememberUpdatedState(callbacks.onCreateFileTreeEntry)
    val onFocus = remember { { node: AslFileTreeNode -> latestOnFocus(node.id) } }
    val onToggle = remember { { id: String -> latestOnToggle(id) } }
    val onSelect = remember { { node: AslFileTreeNode -> latestOnSelectFile(node.id, node.name) } }
    val onAction = remember {
        { node: AslFileTreeNode, action: AslFileTreeAction ->
            latestOnFileTreeAction(action.toEditorAction(), node.id, node.name, node.children != null)
        }
    }
    val onCreate = remember { { kind: EditorFileCreateKind -> latestOnCreate(kind, null) } }

    var fileTreeSearchOpen by remember { mutableStateOf(false) }
    val onToggleSearch = { fileTreeSearchOpen = !fileTreeSearchOpen }
    val toolWindowWidth = rememberAslToolWindowWidth()
    AslToolWindowPanel(
        title = stringResource(R.string.editor_project),
        width = toolWindowWidth,
        onClose = callbacks.onDismiss,
        scrollable = !fileTreeSearchOpen,
        actions = { EditorFilesToolPanelActions(fileTreeSearchOpen, onToggleSearch, onCreate) },
    ) {
        if (fileTreeSearchOpen) {
            FileTreeSearchPanel(
                fileTree = state.fileTree,
                onOpenFile = callbacks.onSelectFile,
                onRevealFolder = callbacks.onRevealFileTreeNode,
                onClose = { fileTreeSearchOpen = false },
            )
        } else {
            AslStateCrossfade(targetState = state.isLoadingFileTree, label = "fileTreeLoading") { loading ->
                if (loading) {
                    AslSkeleton(variant = AslSkeletonVariant.List, rows = 5)
                } else {
                    AslFileTree(
                        items = treeItems,
                        expandedIds = state.expandedFolderIds,
                        selectedId = state.selectedFileId,
                        actionsEnabled = true,
                        canPaste = state.canPasteFileTreeEntry,
                        onFocus = onFocus,
                        onToggle = onToggle,
                        onSelect = onSelect,
                        onAction = onAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorFilesToolPanelActions(
    searchOpen: Boolean,
    onToggleSearch: () -> Unit,
    onCreate: (EditorFileCreateKind) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        AslIconButton(
            icon = "search",
            contentDescription = stringResource(R.string.editor_search_project),
            onClick = onToggleSearch,
            active = searchOpen,
            size = 32.dp,
            iconSize = 16.dp,
        )
        FileTreeCreateMenu(onCreate = onCreate)
    }
}

@Composable
private fun FileTreeCreateMenu(onCreate: (EditorFileCreateKind) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val latestOnCreate by rememberUpdatedState(onCreate)
    val toggleOpen = remember { { open = !open } }
    val dismiss = remember { { open = false } }
    Box {
        AslIconButton(
            icon = "file-plus-2",
            contentDescription = stringResource(R.string.editor_new_file_or_folder),
            onClick = toggleOpen,
            active = open,
            size = 32.dp,
            iconSize = 16.dp,
        )
        if (open) {
            AslDropdownMenu(
                expanded = true,
                onDismissRequest = dismiss,
            ) {
                AslDropdownMenuItem(
                    label = stringResource(R.string.editor_new_file),
                    icon = "file-plus-2",
                    onClick = {
                        dismiss()
                        latestOnCreate(EditorFileCreateKind.File)
                    },
                )
                AslDropdownMenuItem(
                    label = stringResource(R.string.editor_new_folder),
                    icon = "folder",
                    onClick = {
                        dismiss()
                        latestOnCreate(EditorFileCreateKind.Folder)
                    },
                )
            }
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
