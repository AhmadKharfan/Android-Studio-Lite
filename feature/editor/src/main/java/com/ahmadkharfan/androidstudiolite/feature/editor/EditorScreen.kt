package com.ahmadkharfan.androidstudiolite.feature.editor
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslIconButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslFindBar
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDialog
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDialogVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslBottomToolPanel
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslBreadcrumbBar
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslEditorToolbar
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslFileTab
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslFileTabBar
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslBottomPanelTab
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslStatusBar
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslStatusBarEntry
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslStatusTone
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslOverflowMenuEntry
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslLinearProgress
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextField
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorSession
import com.ahmadkharfan.androidstudiolite.feature.editor.view.AslEditableCodeEditor
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.feature.editor.components.EditorBottomPanelContent
import com.ahmadkharfan.androidstudiolite.feature.editor.components.EditorDockedPanel
import com.ahmadkharfan.androidstudiolite.feature.editor.components.EditorDrawer
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffTarget
import java.io.File

private data class GitNavigationCallbacks(
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
fun EditorRoute(
    projectId: String,
    onCloseProject: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAiAgentSettings: () -> Unit,
    onOpenGitDiff: (String, GitDiffTarget) -> Unit,
    onOpenGitHistory: (String) -> Unit,
    onOpenGitBlame: (String) -> Unit,
    onOpenBranches: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenStashes: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenConflicts: () -> Unit,
    openConflictPath: String? = null,
    onConflictPathOpened: () -> Unit = {},
    viewModel: EditorViewModel = koinViewModel { parametersOf(projectId) },
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {  }

    LaunchedEffect(openConflictPath) {
        openConflictPath?.let {
            viewModel.onOpenFile(it, File(it).name)
            onConflictPathOpened()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                EditorEffect.CloseProject -> onCloseProject()
                EditorEffect.OpenSettings -> onOpenSettings()
                EditorEffect.OpenAiAgentSettings -> onOpenAiAgentSettings()
                EditorEffect.RequestNotificationsPermission -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }
    }


    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onAppForegrounded()
                Lifecycle.Event.ON_STOP -> viewModel.flushPendingSaves()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    EditorScreen(
        uiState = uiState,
        interactionListener = viewModel,
        sessionFor = viewModel::sessionFor,
        onEdited = viewModel::onSessionEdited,
        onCaretMoved = viewModel::onCaretMoved,
        gitNavigation = GitNavigationCallbacks(
            openDiff = onOpenGitDiff,
            openFileHistory = onOpenGitHistory,
            openBlame = onOpenGitBlame,
            openBranches = onOpenBranches,
            openTags = onOpenTags,
            openStashes = onOpenStashes,
            openHistory = onOpenHistory,
            openConflicts = onOpenConflicts,
        ),
    )
}

@Composable
private fun EditorScreen(
    uiState: EditorUiState,
    interactionListener: EditorInteractionListener,
    sessionFor: (String?) -> EditorSession?,
    onEdited: (String) -> Unit,
    onCaretMoved: (Int, Int) -> Unit,
    gitNavigation: GitNavigationCallbacks,
) {
    val colors = AslTheme.colors
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            interactionListener.onSnackbarShown()
        }
    }
    Scaffold(
        containerColor = colors.editorCanvas,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        val density = LocalDensity.current
        val keyboardOpen = WindowInsets.ime.getBottom(density) > 0
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding(),
        ) {
            val isTablet = maxWidth >= TABLET_BREAKPOINT
            Column(modifier = Modifier.fillMaxSize()) {
                EditorTopBar(uiState = uiState, interactionListener = interactionListener, isTablet = isTablet)
                EditorContentArea(
                    uiState = uiState,
                    interactionListener = interactionListener,
                    sessionFor = sessionFor,
                    onEdited = onEdited,
                    onCaretMoved = onCaretMoved,
                    gitNavigation = gitNavigation,
                    isTablet = isTablet,
                    keyboardOpen = keyboardOpen,
                    colors = colors,
                    modifier = Modifier.weight(1f).fillMaxWidth().imePadding(),
                )
            }
            if (!isTablet) {
                EditorDrawerOverlay(
                    uiState = uiState,
                    interactionListener = interactionListener,
                    gitNavigation = gitNavigation,
                )
            }
            uiState.installConflict?.let { conflict ->
                AslDialog(
                    title = "Uninstall the existing app?",
                    body = "A different-signed version of ${conflict.applicationId} is already installed, " +
                        "so this build can't replace it. Uninstalling removes that app's data.",
                    variant = AslDialogVariant.Confirm,
                    destructive = true,
                    confirmLabel = "Uninstall & reinstall",
                    cancelLabel = "Cancel",
                    onConfirm = interactionListener::onConfirmInstallConflictUninstall,
                    onDismiss = interactionListener::onDismissInstallConflict,
                )
            }
            EditorFileOperationDialog(uiState.fileOperationDialog, interactionListener)
        }
    }
}

@Composable
private fun EditorFileOperationDialog(
    dialog: EditorFileOperationDialogUiState,
    interactionListener: EditorInteractionListener,
) {
    when (dialog) {
        EditorFileOperationDialogUiState.None -> Unit
        is EditorFileOperationDialogUiState.Create -> {
            var name by remember(dialog) { mutableStateOf("") }
            val isFile = dialog.kind == EditorFileCreateKind.File
            AslDialog(
                title = if (isFile) "New file" else "New folder",
                body = "Create in ${dialog.parentName}",
                variant = AslDialogVariant.Input,
                confirmLabel = "Create",
                cancelLabel = "Cancel",
                onConfirm = { interactionListener.onConfirmCreateFileTreeEntry(name) },
                onDismiss = interactionListener::onDismissFileOperationDialog,
                inputContent = {
                    AslTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = if (isFile) "File name" else "Folder name",
                        placeholder = if (isFile) "Example.kt" else "feature",
                        leadingIcon = if (isFile) "file-code" else "folder",
                        helper = "Use one name, without / or \\",
                    )
                },
            )
        }
        is EditorFileOperationDialogUiState.Rename -> {
            var name by remember(dialog) { mutableStateOf(dialog.currentName) }
            AslDialog(
                title = "Rename",
                body = dialog.currentName,
                variant = AslDialogVariant.Input,
                confirmLabel = "Rename",
                cancelLabel = "Cancel",
                onConfirm = { interactionListener.onConfirmRenameFileTreeEntry(name) },
                onDismiss = interactionListener::onDismissFileOperationDialog,
                inputContent = {
                    AslTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "New name",
                        leadingIcon = "pencil",
                    )
                },
            )
        }
        is EditorFileOperationDialogUiState.Delete -> AslDialog(
            title = if (dialog.isDirectory) "Delete folder?" else "Delete file?",
            body = "Delete ${dialog.name}${if (dialog.isDirectory) " and everything inside it" else ""}. This cannot be undone.",
            variant = AslDialogVariant.Confirm,
            confirmLabel = "Delete",
            cancelLabel = "Cancel",
            destructive = true,
            onConfirm = interactionListener::onConfirmDeleteFileTreeEntry,
            onDismiss = interactionListener::onDismissFileOperationDialog,
        )
    }
}

private val TABLET_BREAKPOINT = 600.dp

@Composable
private fun EditorTopBar(
    uiState: EditorUiState,
    interactionListener: EditorInteractionListener,
    isTablet: Boolean,
) {
    val activeTab = uiState.activeTab
    Column(modifier = Modifier.fillMaxWidth().zIndex(2f)) {
        AslEditorToolbar(
            projectName = uiState.projectName.ifBlank { "Loading…" },
            running = uiState.running,
            onRun = { interactionListener.onRunProject() },
            onMenu = { interactionListener.onToggleMenu() },
            actions = {
                AslIconButton(icon = "undo-2", contentDescription = "Undo", onClick = { interactionListener.onUndo() })
                AslIconButton(icon = "redo-2", contentDescription = "Redo", onClick = { interactionListener.onRedo() })
            },
            overflowItems = listOf(
                AslOverflowMenuEntry.Item("Find in file", icon = "search", shortcut = "⌘F"),
                AslOverflowMenuEntry.Item("Reformat code", icon = "align-left"),
                AslOverflowMenuEntry.Divider,
                AslOverflowMenuEntry.Item(uiState.releaseBuildLabel, icon = "package"),
                AslOverflowMenuEntry.Divider,
                AslOverflowMenuEntry.Item("Close project", icon = "x"),
            ),
            onOverflowSelect = { item, _ ->
                when (item.label) {
                    "Find in file" -> interactionListener.onToggleFindBar()
                    "Reformat code" -> interactionListener.onReformatCode()
                    "Close project" -> interactionListener.onCloseProject()
                    uiState.releaseBuildLabel -> interactionListener.onBuildRelease()
                }
            },
        )
        AslFileTabBar(
            tabs = uiState.tabs.map { AslFileTab(it.id, it.name, fileIconFor(it.name), it.modified) },
            activeId = uiState.activeTabId,
            onSelect = { interactionListener.onSelectTab(it) },
            onClose = { interactionListener.onCloseTab(it) },
        )
        if (activeTab != null && !isTablet) {
            AslBreadcrumbBar(segments = activeTab.breadcrumb)
        }
    }
}

@Composable
private fun EditorContentArea(
    uiState: EditorUiState,
    interactionListener: EditorInteractionListener,
    sessionFor: (String?) -> EditorSession?,
    onEdited: (String) -> Unit,
    onCaretMoved: (Int, Int) -> Unit,
    gitNavigation: GitNavigationCallbacks,
    isTablet: Boolean,
    keyboardOpen: Boolean,
    colors: com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme,
    modifier: Modifier = Modifier,
) {
    val terminalPanelActive = uiState.activeBottomTabId == "term" && uiState.bottomPanelHeightDp > 0f
    Column(modifier = modifier) {
        EditorEditingRow(
            uiState = uiState,
            interactionListener = interactionListener,
            sessionFor = sessionFor,
            onEdited = onEdited,
            onCaretMoved = onCaretMoved,
            gitNavigation = gitNavigation,
            isTablet = isTablet,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        if (!keyboardOpen || terminalPanelActive) {
            EditorBottomToolSection(uiState = uiState, interactionListener = interactionListener)
        }
        if (!keyboardOpen) {
            EditorFullStatusBar(uiState = uiState, onOpenBranches = gitNavigation.openBranches)
        } else {
            EditorCompactStatusBar(uiState = uiState)
        }
    }
}

@Composable
private fun EditorEditingRow(
    uiState: EditorUiState,
    interactionListener: EditorInteractionListener,
    sessionFor: (String?) -> EditorSession?,
    onEdited: (String) -> Unit,
    onCaretMoved: (Int, Int) -> Unit,
    gitNavigation: GitNavigationCallbacks,
    isTablet: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        if (isTablet) {
            EditorDockedPanel(
                openTool = uiState.openRailTool,
                projectId = uiState.projectId,
                gitBadge = uiState.gitBadge,
                fileTree = uiState.fileTree,
                expandedFolderIds = uiState.expandedFolderIds,
                selectedFileId = uiState.selectedFileTreeId,
                canPasteFileTreeEntry = uiState.copiedFileTreeEntry != null,
                onSelectTool = { interactionListener.onSelectRailTool(it) },
                onFocusFileTreeNode = { interactionListener.onFocusFileTreeNode(it) },
                onToggleFolder = { interactionListener.onToggleFolder(it) },
                onSelectFile = { id, name -> interactionListener.onOpenFile(id, name) },
                onRevealFileTreeNode = { interactionListener.onRevealFileTreeNode(it) },
                onCreateFileTreeEntry = { kind, parentPath -> interactionListener.onCreateFileTreeEntry(kind, parentPath) },
                onFileTreeAction = { action, id, name, isDirectory ->
                    when (action) {
                        EditorFileTreeAction.ShowHistory -> gitNavigation.openFileHistory(id)
                        EditorFileTreeAction.Blame -> gitNavigation.openBlame(id)
                        else -> interactionListener.onFileTreeAction(action, id, name, isDirectory)
                    }
                },
                onDismiss = { interactionListener.onCloseDrawer() },
                onOpenSettings = { interactionListener.onOpenSettings() },
                onOpenAiAgentSettings = { interactionListener.onOpenAiAgentSettings() },
                onOpenGitDiff = gitNavigation.openDiff,
                onOpenGitHistory = gitNavigation.openHistory,
                onOpenGitBranches = gitNavigation.openBranches,
                onOpenGitTags = gitNavigation.openTags,
                onOpenGitStashes = gitNavigation.openStashes,
                onOpenGitConflicts = gitNavigation.openConflicts,
                onCloseProject = { interactionListener.onCloseProject() },
                selectedVariant = uiState.selectedVariant,
                onSelectVariant = { interactionListener.onSelectVariant(it) },
                availableVariants = uiState.availableVariants,
                runModulePath = uiState.runModulePath,
                isLoadingFileTree = uiState.isLoadingFileTree,
            )
        }
        EditorCodeSurface(
            uiState = uiState,
            interactionListener = interactionListener,
            sessionFor = sessionFor,
            onEdited = onEdited,
            onCaretMoved = onCaretMoved,
            isTablet = isTablet,
            modifier = Modifier.weight(1f).fillMaxSize(),
        )
    }
}

@Composable
private fun EditorCodeSurface(
    uiState: EditorUiState,
    interactionListener: EditorInteractionListener,
    sessionFor: (String?) -> EditorSession?,
    onEdited: (String) -> Unit,
    onCaretMoved: (Int, Int) -> Unit,
    isTablet: Boolean,
    modifier: Modifier = Modifier,
) {
    val activeTab = uiState.activeTab
    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (activeTab != null && isTablet) {
                AslBreadcrumbBar(segments = activeTab.breadcrumb)
            }
            val activeSession = sessionFor(activeTab?.id)
            if (activeTab != null && activeSession != null) {
                AslEditableCodeEditor(
                    session = activeSession,
                    fontSizeSp = uiState.editorFontSize,
                    tabSize = uiState.editorTabSize,
                    colorSchemeId = uiState.editorThemeId,
                    fontFamilyId = uiState.editorFontFamily,
                    onEdited = { onEdited(activeTab.id) },
                    onCaretMoved = onCaretMoved,
                    gitLineStatus = activeTab.gitLineStatus,
                    breakpoints = activeTab.breakpoints,
                    findQuery = if (uiState.findBarOpen) uiState.findQuery else "",
                    findCurrentMatch = uiState.findCurrentMatch,
                    revealNonce = uiState.editorRevealNonce,
                    revealOffset = uiState.editorRevealOffset,
                    projectIndex = uiState.projectIndex,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxSize())
            }
        }
        if (uiState.findBarOpen) {
            AslFindBar(
                query = uiState.findQuery,
                onChange = { interactionListener.onFindQueryChanged(it) },
                matchCount = uiState.findMatchCount,
                currentMatch = uiState.findCurrentMatch,
                onNext = { interactionListener.onFindNext() },
                onPrev = { interactionListener.onFindPrevious() },
                onClose = { interactionListener.onToggleFindBar() },
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@Composable
private fun EditorBottomToolSection(
    uiState: EditorUiState,
    interactionListener: EditorInteractionListener,
) {
    AslBottomToolPanel(
        tabs = uiState.bottomPanelTabs.map { AslBottomPanelTab(it.id, it.label, it.icon, it.count, it.error) },
        activeId = uiState.activeBottomTabId,
        contentHeight = uiState.bottomPanelHeightDp.dp,
        onContentHeightChange = { interactionListener.onBottomPanelHeightChanged(it.value) },
        onSelect = { interactionListener.onSelectBottomTab(it) },
        onToggle = { interactionListener.onToggleBottomPanel() },
    ) {
        EditorBottomPanelContent(
            activeTabId = uiState.activeBottomTabId,
            buildConsole = uiState.buildConsole,
            projectRootPath = uiState.projectRootPath,
            onCancelBuild = { interactionListener.onCancelBuild() },
            onJumpToBuildProblem = { interactionListener.onJumpToBuildProblem(it) },
        )
    }
}

@Composable
private fun EditorFullStatusBar(uiState: EditorUiState, onOpenBranches: () -> Unit) {
    AslStatusBar(
        items = buildList {
            uiState.gitStatusText?.let { add(AslStatusBarEntry.Item(it, icon = "git-branch", onClick = onOpenBranches)) }
            when {
                uiState.running -> add(AslStatusBarEntry.Item("Building"))
                uiState.buildFailed -> add(AslStatusBarEntry.Item("Build failed", icon = "octagon-alert", tone = AslStatusTone.Error))
                else -> add(AslStatusBarEntry.Item("Kotlin"))
            }
            add(AslStatusBarEntry.Spacer)
            add(AslStatusBarEntry.Item("Ln ${uiState.caretLine + 1}, Col ${uiState.caretColumn + 1}"))
            val variantLabel = uiState.selectedVariant.replaceFirstChar { it.uppercase() }
            when {
                uiState.running -> add(AslStatusBarEntry.Item("assemble$variantLabel", tone = AslStatusTone.Warning))
                else -> add(AslStatusBarEntry.Item(variantLabel, icon = "layers"))
            }
        },
    )
}

@Composable
private fun EditorCompactStatusBar(uiState: EditorUiState) {
    AslStatusBar(
        items = buildList {
            add(AslStatusBarEntry.Item("Ln ${uiState.caretLine + 1}, Col ${uiState.caretColumn + 1}"))
        },
    )
}

@Composable
private fun EditorDrawerOverlay(
    uiState: EditorUiState,
    interactionListener: EditorInteractionListener,
    gitNavigation: GitNavigationCallbacks,
) {
    EditorDrawer(
        openTool = uiState.openRailTool,
        projectId = uiState.projectId,
        gitBadge = uiState.gitBadge,
        fileTree = uiState.fileTree,
        expandedFolderIds = uiState.expandedFolderIds,
        selectedFileId = uiState.selectedFileTreeId,
        canPasteFileTreeEntry = uiState.copiedFileTreeEntry != null,
        onSelectTool = { interactionListener.onSelectRailTool(it) },
        onFocusFileTreeNode = { interactionListener.onFocusFileTreeNode(it) },
        onToggleFolder = { interactionListener.onToggleFolder(it) },
        onSelectFile = { id, name -> interactionListener.onOpenFile(id, name) },
        onRevealFileTreeNode = { interactionListener.onRevealFileTreeNode(it) },
        onCreateFileTreeEntry = { kind, parentPath -> interactionListener.onCreateFileTreeEntry(kind, parentPath) },
        onFileTreeAction = { action, id, name, isDirectory ->
            when (action) {
                EditorFileTreeAction.ShowHistory -> gitNavigation.openFileHistory(id)
                EditorFileTreeAction.Blame -> gitNavigation.openBlame(id)
                else -> interactionListener.onFileTreeAction(action, id, name, isDirectory)
            }
        },
        onDismiss = { interactionListener.onCloseDrawer() },
        onOpenSettings = { interactionListener.onOpenSettings() },
        onOpenAiAgentSettings = { interactionListener.onOpenAiAgentSettings() },
        onOpenGitDiff = gitNavigation.openDiff,
        onOpenGitHistory = gitNavigation.openHistory,
        onOpenGitBranches = gitNavigation.openBranches,
        onOpenGitTags = gitNavigation.openTags,
        onOpenGitStashes = gitNavigation.openStashes,
        onOpenGitConflicts = gitNavigation.openConflicts,
        onCloseProject = { interactionListener.onCloseProject() },
        selectedVariant = uiState.selectedVariant,
        onSelectVariant = { interactionListener.onSelectVariant(it) },
        availableVariants = uiState.availableVariants,
        runModulePath = uiState.runModulePath,
        isLoadingFileTree = uiState.isLoadingFileTree,
        modifier = Modifier.fillMaxSize(),
    )
}
