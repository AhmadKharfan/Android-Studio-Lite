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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextField
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslBreakpoints
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorSession
import com.ahmadkharfan.androidstudiolite.feature.editor.view.AslEditableCodeEditor
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.feature.editor.components.EditorBottomPanelContent
import com.ahmadkharfan.androidstudiolite.feature.editor.components.EditorDockedPanel
import com.ahmadkharfan.androidstudiolite.feature.editor.components.EditorDrawer
import com.ahmadkharfan.androidstudiolite.feature.editor.components.MarkdownPreviewPane
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorLanguage
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffTarget
import java.io.File
import com.ahmadkharfan.androidstudiolite.core.common.R as CommonR

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
            val isTablet = maxWidth >= AslBreakpoints.tablet
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
                    title = stringResource(R.string.editor_uninstall_title),
                    body = stringResource(R.string.editor_uninstall_body, conflict.applicationId),
                    variant = AslDialogVariant.Confirm,
                    destructive = true,
                    confirmLabel = stringResource(R.string.editor_uninstall_reinstall),
                    cancelLabel = stringResource(CommonR.string.action_cancel),
                    onConfirm = interactionListener::onConfirmInstallConflictUninstall,
                    onDismiss = interactionListener::onDismissInstallConflict,
                )
            }
            EditorFileOperationDialog(uiState.fileOperationDialog, interactionListener)
        }
    }
}

private val WHITESPACE_AFTER_DOT = Regex("\\.\\s+")

private fun sanitizeFileEntryName(raw: String): String = raw.replace(WHITESPACE_AFTER_DOT, ".")

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
                title = stringResource(if (isFile) R.string.editor_new_file else R.string.editor_new_folder),
                body = stringResource(R.string.editor_create_in, dialog.parentName),
                variant = AslDialogVariant.Input,
                confirmLabel = stringResource(CommonR.string.action_create),
                cancelLabel = stringResource(CommonR.string.action_cancel),
                onConfirm = { interactionListener.onConfirmCreateFileTreeEntry(name) },
                onDismiss = interactionListener::onDismissFileOperationDialog,
                inputContent = {
                    AslTextField(
                        value = name,
                        onValueChange = { name = sanitizeFileEntryName(it) },
                        label = stringResource(if (isFile) R.string.editor_file_name else R.string.editor_folder_name),
                        placeholder = stringResource(if (isFile) R.string.editor_file_placeholder else R.string.editor_folder_placeholder),
                        leadingIcon = if (isFile) "file-code" else "folder",
                        helper = stringResource(R.string.editor_name_helper),
                    )
                },
            )
        }
        is EditorFileOperationDialogUiState.Rename -> {
            var name by remember(dialog) { mutableStateOf(dialog.currentName) }
            AslDialog(
                title = stringResource(CommonR.string.action_rename),
                body = dialog.currentName,
                variant = AslDialogVariant.Input,
                confirmLabel = stringResource(CommonR.string.action_rename),
                cancelLabel = stringResource(CommonR.string.action_cancel),
                onConfirm = { interactionListener.onConfirmRenameFileTreeEntry(name) },
                onDismiss = interactionListener::onDismissFileOperationDialog,
                inputContent = {
                    AslTextField(
                        value = name,
                        onValueChange = { name = sanitizeFileEntryName(it) },
                        label = stringResource(R.string.editor_new_name),
                        leadingIcon = "pencil",
                    )
                },
            )
        }
        is EditorFileOperationDialogUiState.Delete -> AslDialog(
            title = stringResource(if (dialog.isDirectory) R.string.editor_delete_folder_title else R.string.editor_delete_file_title),
            body = stringResource(
                if (dialog.isDirectory) R.string.editor_delete_folder_body else R.string.editor_delete_file_body,
                dialog.name,
            ),
            variant = AslDialogVariant.Confirm,
            confirmLabel = stringResource(CommonR.string.action_delete),
            cancelLabel = stringResource(CommonR.string.action_cancel),
            destructive = true,
            onConfirm = interactionListener::onConfirmDeleteFileTreeEntry,
            onDismiss = interactionListener::onDismissFileOperationDialog,
        )
    }
}

@Composable
private fun EditorTopBar(
    uiState: EditorUiState,
    interactionListener: EditorInteractionListener,
    isTablet: Boolean,
) {
    val activeTab = uiState.activeTab
    val findInFile = stringResource(R.string.editor_find_file)
    val reformatCode = stringResource(R.string.editor_reformat_code)
    val closeProject = stringResource(R.string.editor_close_project)
    val loading = stringResource(R.string.editor_loading)
    val overflowItems = listOf(
        AslOverflowMenuEntry.Item(findInFile, icon = "search", shortcut = "⌘F") {
            interactionListener.onToggleFindBar()
        },
        AslOverflowMenuEntry.Item(reformatCode, icon = "align-left") {
            interactionListener.onReformatCode()
        },
        AslOverflowMenuEntry.Divider,
        AslOverflowMenuEntry.Item(uiState.releaseBuildLabel, icon = "package") {
            interactionListener.onBuildRelease()
        },
        AslOverflowMenuEntry.Divider,
        AslOverflowMenuEntry.Item(closeProject, icon = "x") {
            interactionListener.onCloseProject()
        },
    )
    val onSelectTab = remember(interactionListener) { { id: String -> interactionListener.onSelectTab(id) } }
    val onCloseTab = remember(interactionListener) { { id: String -> interactionListener.onCloseTab(id) } }
    Column(modifier = Modifier.fillMaxWidth().zIndex(2f)) {
        AslEditorToolbar(
            projectName = uiState.projectName.ifBlank { loading },
            running = uiState.running,
            onRun = {
                if (uiState.running) interactionListener.onCancelBuild()
                else interactionListener.onRunProject()
            },
            onMenu = { interactionListener.onToggleMenu() },
            actions = {
                EditorToolbarEditActions(
                    interactionListener = interactionListener,
                    showMarkdownPreviewToggle = activeTab?.language == EditorLanguage.Markdown,
                    markdownPreview = uiState.markdownPreview,
                )
            },
            overflowItems = overflowItems,
        )
        AslFileTabBar(
            tabs = uiState.tabs.map { AslFileTab(it.id, it.name, fileIconFor(it.name), it.modified) },
            activeId = uiState.activeTabId,
            onSelect = onSelectTab,
            onClose = onCloseTab,
        )
        if (activeTab != null && !isTablet) {
            AslBreadcrumbBar(segments = activeTab.breadcrumb)
        }
    }
}

@Composable
private fun EditorToolbarEditActions(
    interactionListener: EditorInteractionListener,
    showMarkdownPreviewToggle: Boolean = false,
    markdownPreview: Boolean = true,
) {
    val onUndo = remember(interactionListener) { interactionListener::onUndo }
    val onRedo = remember(interactionListener) { interactionListener::onRedo }
    AslIconButton(icon = "undo-2", contentDescription = stringResource(R.string.editor_undo), onClick = onUndo)
    AslIconButton(icon = "redo-2", contentDescription = stringResource(R.string.editor_redo), onClick = onRedo)
    if (showMarkdownPreviewToggle) {
        AslIconButton(
            icon = if (markdownPreview) "code" else "eye",
            contentDescription = stringResource(if (markdownPreview) R.string.editor_markdown_edit else R.string.editor_markdown_preview),
            onClick = { interactionListener.onToggleMarkdownPreview() },
        )
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
    val drawerCallbacks = rememberEditorDrawerCallbacks(interactionListener, gitNavigation)
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
                onSelectTool = drawerCallbacks.onSelectTool,
                onFocusFileTreeNode = drawerCallbacks.onFocusFileTreeNode,
                onToggleFolder = drawerCallbacks.onToggleFolder,
                onSelectFile = drawerCallbacks.onSelectFile,
                onRevealFileTreeNode = drawerCallbacks.onRevealFileTreeNode,
                onCreateFileTreeEntry = drawerCallbacks.onCreateFileTreeEntry,
                onFileTreeAction = drawerCallbacks.onFileTreeAction,
                onDismiss = drawerCallbacks.onDismiss,
                onOpenSettings = drawerCallbacks.onOpenSettings,
                onOpenAiAgentSettings = drawerCallbacks.onOpenAiAgentSettings,
                onOpenGitDiff = gitNavigation.openDiff,
                onOpenGitHistory = gitNavigation.openHistory,
                onOpenGitBranches = gitNavigation.openBranches,
                onOpenGitTags = gitNavigation.openTags,
                onOpenGitStashes = gitNavigation.openStashes,
                onOpenGitConflicts = gitNavigation.openConflicts,
                onCloseProject = drawerCallbacks.onCloseProject,
                selectedVariant = uiState.selectedVariant,
                onSelectVariant = drawerCallbacks.onSelectVariant,
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
                val showMarkdownPreview = activeTab.language == EditorLanguage.Markdown &&
                    uiState.markdownPreview
                if (showMarkdownPreview) {
                    MarkdownPreviewPane(
                        markdown = activeSession.text,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                } else {
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
                }
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
            onJumpToBuildProblem = { interactionListener.onJumpToBuildProblem(it) },
        )
    }
}

@Composable
private fun EditorFullStatusBar(uiState: EditorUiState, onOpenBranches: () -> Unit) {
    val building = stringResource(R.string.editor_building)
    val buildFailed = stringResource(R.string.editor_build_failed)
    val plainText = stringResource(R.string.editor_plain_text)
    val caretPosition = stringResource(R.string.editor_caret_position, uiState.caretLine + 1, uiState.caretColumn + 1)
    AslStatusBar(
        items = buildList {
            uiState.gitStatusText?.let { add(AslStatusBarEntry.Item(it, icon = "git-branch", onClick = onOpenBranches)) }
            when {
                uiState.running -> add(AslStatusBarEntry.Item(building))
                uiState.buildFailed -> add(AslStatusBarEntry.Item(buildFailed, icon = "octagon-alert", tone = AslStatusTone.Error))
                else -> add(AslStatusBarEntry.Item(uiState.activeTab?.language?.displayName ?: plainText))
            }
            add(AslStatusBarEntry.Spacer)
            add(AslStatusBarEntry.Item(caretPosition))
            val statusVariant = if (uiState.running) uiState.buildConsole.request?.variantName else null
            val variantLabel = (statusVariant ?: uiState.selectedVariant)
                .replaceFirstChar { it.uppercase() }
            when {
                uiState.running -> add(AslStatusBarEntry.Item("assemble$variantLabel", tone = AslStatusTone.Warning))
                else -> add(AslStatusBarEntry.Item(variantLabel, icon = "layers"))
            }
        },
    )
}

@Composable
private fun EditorCompactStatusBar(uiState: EditorUiState) {
    val caretPosition = stringResource(R.string.editor_caret_position, uiState.caretLine + 1, uiState.caretColumn + 1)
    AslStatusBar(
        items = buildList {
            add(AslStatusBarEntry.Item(caretPosition))
        },
    )
}

@Composable
private fun EditorDrawerOverlay(
    uiState: EditorUiState,
    interactionListener: EditorInteractionListener,
    gitNavigation: GitNavigationCallbacks,
) {
    val drawerCallbacks = rememberEditorDrawerCallbacks(interactionListener, gitNavigation)
    EditorDrawer(
        openTool = uiState.openRailTool,
        projectId = uiState.projectId,
        gitBadge = uiState.gitBadge,
        fileTree = uiState.fileTree,
        expandedFolderIds = uiState.expandedFolderIds,
        selectedFileId = uiState.selectedFileTreeId,
        canPasteFileTreeEntry = uiState.copiedFileTreeEntry != null,
        onSelectTool = drawerCallbacks.onSelectTool,
        onFocusFileTreeNode = drawerCallbacks.onFocusFileTreeNode,
        onToggleFolder = drawerCallbacks.onToggleFolder,
        onSelectFile = drawerCallbacks.onSelectFile,
        onRevealFileTreeNode = drawerCallbacks.onRevealFileTreeNode,
        onCreateFileTreeEntry = drawerCallbacks.onCreateFileTreeEntry,
        onFileTreeAction = drawerCallbacks.onFileTreeAction,
        onDismiss = drawerCallbacks.onDismiss,
        onOpenSettings = drawerCallbacks.onOpenSettings,
        onOpenAiAgentSettings = drawerCallbacks.onOpenAiAgentSettings,
        onOpenGitDiff = gitNavigation.openDiff,
        onOpenGitHistory = gitNavigation.openHistory,
        onOpenGitBranches = gitNavigation.openBranches,
        onOpenGitTags = gitNavigation.openTags,
        onOpenGitStashes = gitNavigation.openStashes,
        onOpenGitConflicts = gitNavigation.openConflicts,
        onCloseProject = drawerCallbacks.onCloseProject,
        selectedVariant = uiState.selectedVariant,
        onSelectVariant = drawerCallbacks.onSelectVariant,
        availableVariants = uiState.availableVariants,
        runModulePath = uiState.runModulePath,
        isLoadingFileTree = uiState.isLoadingFileTree,
        modifier = Modifier.fillMaxSize(),
    )
}

private data class EditorDrawerCallbacks(
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

@Composable
private fun rememberEditorDrawerCallbacks(
    interactionListener: EditorInteractionListener,
    gitNavigation: GitNavigationCallbacks,
): EditorDrawerCallbacks {
    val onSelectTool = remember(interactionListener) { { tool: EditorRailTool -> interactionListener.onSelectRailTool(tool) } }
    val onFocusFileTreeNode = remember(interactionListener) { { id: String -> interactionListener.onFocusFileTreeNode(id) } }
    val onToggleFolder = remember(interactionListener) { { id: String -> interactionListener.onToggleFolder(id) } }
    val onSelectFile = remember(interactionListener) { { id: String, name: String -> interactionListener.onOpenFile(id, name) } }
    val onRevealFileTreeNode = remember(interactionListener) { { id: String -> interactionListener.onRevealFileTreeNode(id) } }
    val onCreateFileTreeEntry = remember(interactionListener) {
        { kind: EditorFileCreateKind, parentPath: String? -> interactionListener.onCreateFileTreeEntry(kind, parentPath) }
    }
    val onFileTreeAction = remember(interactionListener, gitNavigation) {
        { action: EditorFileTreeAction, id: String, name: String, isDirectory: Boolean ->
            when (action) {
                EditorFileTreeAction.ShowHistory -> gitNavigation.openFileHistory(id)
                EditorFileTreeAction.Blame -> gitNavigation.openBlame(id)
                else -> interactionListener.onFileTreeAction(action, id, name, isDirectory)
            }
        }
    }
    val onDismiss = remember(interactionListener) { interactionListener::onCloseDrawer }
    val onOpenSettings = remember(interactionListener) { interactionListener::onOpenSettings }
    val onOpenAiAgentSettings = remember(interactionListener) { interactionListener::onOpenAiAgentSettings }
    val onCloseProject = remember(interactionListener) { interactionListener::onCloseProject }
    val onSelectVariant = remember(interactionListener) { { variant: String -> interactionListener.onSelectVariant(variant) } }
    return remember(
        onSelectTool,
        onFocusFileTreeNode,
        onToggleFolder,
        onSelectFile,
        onRevealFileTreeNode,
        onCreateFileTreeEntry,
        onFileTreeAction,
        onDismiss,
        onOpenSettings,
        onOpenAiAgentSettings,
        onCloseProject,
        onSelectVariant,
    ) {
        EditorDrawerCallbacks(
            onSelectTool = onSelectTool,
            onFocusFileTreeNode = onFocusFileTreeNode,
            onToggleFolder = onToggleFolder,
            onSelectFile = onSelectFile,
            onRevealFileTreeNode = onRevealFileTreeNode,
            onCreateFileTreeEntry = onCreateFileTreeEntry,
            onFileTreeAction = onFileTreeAction,
            onDismiss = onDismiss,
            onOpenSettings = onOpenSettings,
            onOpenAiAgentSettings = onOpenAiAgentSettings,
            onCloseProject = onCloseProject,
            onSelectVariant = onSelectVariant,
        )
    }
}
