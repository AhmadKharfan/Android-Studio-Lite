package com.ahmadkharfan.androidstudiolite.feature.editor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.ahmadkharfan.androidstudiolite.designsystem.animation.AslStateCrossfade
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
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslBanner
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslBannerTone
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslLinearProgress
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslMemoryChartMini
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslMemoryChartTone
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextField
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslMotion
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.Diagnostic
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorSession
import com.ahmadkharfan.androidstudiolite.feature.editor.view.AslEditableCodeEditor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.feature.editor.components.EditorBottomPanelContent
import com.ahmadkharfan.androidstudiolite.feature.editor.components.EditorDockedPanel
import com.ahmadkharfan.androidstudiolite.feature.editor.components.EditorDrawer

@Composable
fun EditorRoute(
    projectId: String,
    onCloseProject: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAiAgentSettings: () -> Unit,
    viewModel: EditorViewModel = koinViewModel { parametersOf(projectId) },
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                EditorEffect.CloseProject -> onCloseProject()
                EditorEffect.OpenSettings -> onOpenSettings()
                EditorEffect.OpenAiAgentSettings -> onOpenAiAgentSettings()
            }
        }
    }

    // Persist unsaved edits whenever the editor stops (app backgrounded, screen off, navigation away).
    // ON_STOP fires before the process can be killed, so a debounce still in flight isn't lost.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flushPendingSaves()
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
        onDiagnostics = viewModel::onDiagnostics,
    )
}

@Composable
private fun EditorScreen(
    uiState: EditorUiState,
    interactionListener: EditorInteractionListener,
    sessionFor: (String?) -> EditorSession?,
    onEdited: (String) -> Unit,
    onCaretMoved: (Int, Int) -> Unit,
    onDiagnostics: (String, List<Diagnostic>) -> Unit = { _, _ -> },
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
                    onDiagnostics = onDiagnostics,
                    isTablet = isTablet,
                    keyboardOpen = keyboardOpen,
                    colors = colors,
                    modifier = Modifier.weight(1f).fillMaxWidth().imePadding(),
                )
            }
            if (!isTablet) {
                EditorDrawerOverlay(uiState = uiState, interactionListener = interactionListener)
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
                AslOverflowMenuEntry.Item("Sync Gradle", icon = "refresh-cw"),
                AslOverflowMenuEntry.Divider,
                AslOverflowMenuEntry.Item("Build APK (release)", icon = "package"),
                AslOverflowMenuEntry.Item("Build AAB (release)", icon = "package"),
                AslOverflowMenuEntry.Divider,
                AslOverflowMenuEntry.Item("Simulate build failure", icon = "octagon-alert"),
                AslOverflowMenuEntry.Item("Simulate memory pressure", icon = "memory-stick"),
                AslOverflowMenuEntry.Item("Simulate LSP reindex", icon = "loader"),
                AslOverflowMenuEntry.Divider,
                AslOverflowMenuEntry.Item("Close project", icon = "x"),
            ),
            onOverflowSelect = { item, _ ->
                when (item.label) {
                    "Find in file" -> interactionListener.onToggleFindBar()
                    "Close project" -> interactionListener.onCloseProject()
                    "Build APK (release)" -> interactionListener.onBuildReleaseApk()
                    "Build AAB (release)" -> interactionListener.onBuildReleaseBundle()
                    "Simulate build failure" -> interactionListener.onSimulateBuildFailure()
                    "Simulate memory pressure" -> interactionListener.onToggleMemoryPressure()
                    "Simulate LSP reindex" -> interactionListener.onSimulateLspReindex()
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
    onDiagnostics: (String, List<Diagnostic>) -> Unit,
    isTablet: Boolean,
    keyboardOpen: Boolean,
    colors: com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        EditorEditingRow(
            uiState = uiState,
            interactionListener = interactionListener,
            sessionFor = sessionFor,
            onEdited = onEdited,
            onCaretMoved = onCaretMoved,
            onDiagnostics = onDiagnostics,
            isTablet = isTablet,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        if (!keyboardOpen) {
            EditorMemoryPressureBanner(uiState = uiState, interactionListener = interactionListener, colors = colors)
            EditorLspReindexBanner(uiState = uiState, colors = colors)
            EditorBottomToolSection(uiState = uiState, interactionListener = interactionListener)
            EditorFullStatusBar(uiState = uiState)
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
    onDiagnostics: (String, List<Diagnostic>) -> Unit,
    isTablet: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        if (isTablet) {
            EditorDockedPanel(
                openTool = uiState.openRailTool,
                projectId = uiState.projectId,
                fileTree = uiState.fileTree,
                expandedFolderIds = uiState.expandedFolderIds,
                selectedFileId = uiState.selectedFileTreeId,
                canPasteFileTreeEntry = uiState.copiedFileTreeEntry != null,
                onSelectTool = { interactionListener.onSelectRailTool(it) },
                onFocusFileTreeNode = { interactionListener.onFocusFileTreeNode(it) },
                onToggleFolder = { interactionListener.onToggleFolder(it) },
                onSelectFile = { id, name -> interactionListener.onOpenFile(id, name) },
                onCreateFileTreeEntry = { kind, parentPath -> interactionListener.onCreateFileTreeEntry(kind, parentPath) },
                onFileTreeAction = { action, id, name, isDirectory -> interactionListener.onFileTreeAction(action, id, name, isDirectory) },
                onDismiss = { interactionListener.onCloseDrawer() },
                onOpenSettings = { interactionListener.onOpenSettings() },
                onOpenAiAgentSettings = { interactionListener.onOpenAiAgentSettings() },
                onCloseProject = { interactionListener.onCloseProject() },
                isLoadingFileTree = uiState.isLoadingFileTree,
            )
        }
        EditorCodeSurface(
            uiState = uiState,
            interactionListener = interactionListener,
            sessionFor = sessionFor,
            onEdited = onEdited,
            onCaretMoved = onCaretMoved,
            onDiagnostics = onDiagnostics,
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
    onDiagnostics: (String, List<Diagnostic>) -> Unit,
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
                    onEdited = { onEdited(activeTab.id) },
                    onCaretMoved = onCaretMoved,
                    gitLineStatus = activeTab.gitLineStatus,
                    breakpoints = activeTab.breakpoints,
                    findQuery = if (uiState.findBarOpen) uiState.findQuery else "",
                    findCurrentMatch = uiState.findCurrentMatch,
                    lspKotlin = uiState.kotlinLspEnabled,
                    lspJava = uiState.javaLspEnabled,
                    lspXml = uiState.xmlLspEnabled,
                    onDiagnostics = { onDiagnostics(activeTab.id, it) },
                    revealNonce = uiState.diagnosticRevealNonce,
                    revealOffset = uiState.diagnosticRevealOffset,
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
private fun EditorMemoryPressureBanner(
    uiState: EditorUiState,
    interactionListener: EditorInteractionListener,
    colors: com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme,
) {
    AnimatedVisibility(
        visible = uiState.memoryPressureActive,
        enter = expandVertically(AslMotion.enterSpec()) + fadeIn(AslMotion.enterSpec()),
        exit = shrinkVertically(AslMotion.exitSpec()) + fadeOut(AslMotion.exitSpec()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgElevated)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            AslBanner(
                tone = AslBannerTone.Warning,
                message = "Memory is running low — close unused projects.",
                actionLabel = "Free up",
                onAction = { interactionListener.onFreeUpMemory() },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { interactionListener.onToggleMemoryChartExpanded() },
            )
            AnimatedVisibility(
                visible = uiState.memoryChartExpanded,
                enter = expandVertically(AslMotion.enterSpec()) + fadeIn(AslMotion.enterSpec()),
                exit = shrinkVertically(AslMotion.exitSpec()) + fadeOut(AslMotion.exitSpec()),
            ) {
                AslMemoryChartMini(
                    label = "Heap",
                    value = uiState.heapUsedMb,
                    max = uiState.heapMaxMb,
                    series = uiState.heapSeries,
                    tone = AslMemoryChartTone.Warning,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun EditorLspReindexBanner(
    uiState: EditorUiState,
    colors: com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme,
) {
    AnimatedVisibility(
        visible = uiState.lspUpdating,
        enter = expandVertically(AslMotion.enterSpec()) + fadeIn(AslMotion.enterSpec()),
        exit = shrinkVertically(AslMotion.exitSpec()) + fadeOut(AslMotion.exitSpec()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgElevated)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            AslLinearProgress(label = "Kotlin language server updating", detail = "indexing 412 files")
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
        expanded = uiState.bottomPanelExpanded,
        onSelect = { interactionListener.onSelectBottomTab(it) },
        onToggle = { interactionListener.onToggleBottomPanel() },
    ) {
        AslStateCrossfade(targetState = uiState.activeBottomTabId, label = "bottomPanelContent") { tabId ->
            EditorBottomPanelContent(
                activeTabId = tabId,
                buildConsole = uiState.buildConsole,
                appLogLines = uiState.appLogLines,
                diagnostics = uiState.activeDiagnostics,
                activeFileName = uiState.activeTab?.name,
                onCancelBuild = { interactionListener.onCancelBuild() },
                onJumpToBuildProblem = { interactionListener.onJumpToBuildProblem(it) },
                onJumpToDiagnostic = { interactionListener.onJumpToDiagnostic(it) },
            )
        }
    }
}

@Composable
private fun EditorFullStatusBar(uiState: EditorUiState) {
    AslStatusBar(
        items = buildList {
            add(AslStatusBarEntry.Item("main ●", icon = "git-branch"))
            when {
                uiState.lspUpdating -> add(AslStatusBarEntry.Item("LSP updating", icon = "loader", spin = true))
                uiState.running -> add(AslStatusBarEntry.Item("Building"))
                uiState.buildFailed -> add(AslStatusBarEntry.Item("Build failed", icon = "octagon-alert", tone = AslStatusTone.Error))
                else -> add(AslStatusBarEntry.Item("Kotlin"))
            }
            add(AslStatusBarEntry.Spacer)
            add(AslStatusBarEntry.Item("Ln ${uiState.caretLine + 1}, Col ${uiState.caretColumn + 1}"))
            if (uiState.errorCount > 0 || uiState.warningCount > 0) {
                add(
                    AslStatusBarEntry.Item(
                        "${uiState.errorCount} ⨯ · ${uiState.warningCount} ⚠",
                        icon = if (uiState.errorCount > 0) "octagon-alert" else "triangle-alert",
                        tone = if (uiState.errorCount > 0) AslStatusTone.Error else AslStatusTone.Warning,
                    ),
                )
            }
            if (uiState.memoryPressureActive) {
                add(AslStatusBarEntry.Item("${uiState.heapUsedMb} / ${uiState.heapMaxMb} MB", icon = "memory-stick", tone = AslStatusTone.Warning))
            }
            when {
                uiState.running -> add(AslStatusBarEntry.Item("assembleDebug", tone = AslStatusTone.Warning))
                !uiState.lspUpdating -> add(AslStatusBarEntry.Item("Synced", icon = "check", tone = AslStatusTone.Success))
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
) {
    EditorDrawer(
        openTool = uiState.openRailTool,
        projectId = uiState.projectId,
        fileTree = uiState.fileTree,
        expandedFolderIds = uiState.expandedFolderIds,
        selectedFileId = uiState.selectedFileTreeId,
        canPasteFileTreeEntry = uiState.copiedFileTreeEntry != null,
        onSelectTool = { interactionListener.onSelectRailTool(it) },
        onFocusFileTreeNode = { interactionListener.onFocusFileTreeNode(it) },
        onToggleFolder = { interactionListener.onToggleFolder(it) },
        onSelectFile = { id, name -> interactionListener.onOpenFile(id, name) },
        onCreateFileTreeEntry = { kind, parentPath -> interactionListener.onCreateFileTreeEntry(kind, parentPath) },
        onFileTreeAction = { action, id, name, isDirectory -> interactionListener.onFileTreeAction(action, id, name, isDirectory) },
        onDismiss = { interactionListener.onCloseDrawer() },
        onOpenSettings = { interactionListener.onOpenSettings() },
        onOpenAiAgentSettings = { interactionListener.onOpenAiAgentSettings() },
        onCloseProject = { interactionListener.onCloseProject() },
        isLoadingFileTree = uiState.isLoadingFileTree,
        modifier = Modifier.fillMaxSize(),
    )
}
