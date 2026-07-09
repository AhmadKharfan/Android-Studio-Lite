package com.example.androidstudiolite.feature.editor
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.example.androidstudiolite.designsystem.animation.AslStateCrossfade
import com.example.androidstudiolite.designsystem.component.buttons.AslIconButton
import com.example.androidstudiolite.designsystem.component.content.AslFindBar
import com.example.androidstudiolite.designsystem.component.navigation.AslBottomToolPanel
import com.example.androidstudiolite.designsystem.component.navigation.AslBreadcrumbBar
import com.example.androidstudiolite.designsystem.component.navigation.AslEditorToolbar
import com.example.androidstudiolite.designsystem.component.navigation.AslFileTab
import com.example.androidstudiolite.designsystem.component.navigation.AslFileTabBar
import com.example.androidstudiolite.designsystem.component.navigation.AslStatusBar
import com.example.androidstudiolite.designsystem.component.navigation.AslStatusBarEntry
import com.example.androidstudiolite.designsystem.component.navigation.AslStatusTone
import com.example.androidstudiolite.designsystem.component.buttons.AslOverflowMenuEntry
import com.example.androidstudiolite.designsystem.component.feedback.AslBanner
import com.example.androidstudiolite.designsystem.component.feedback.AslBannerTone
import com.example.androidstudiolite.designsystem.component.feedback.AslLinearProgress
import com.example.androidstudiolite.designsystem.component.ide.AslMemoryChartMini
import com.example.androidstudiolite.designsystem.component.ide.AslMemoryChartTone
import com.example.androidstudiolite.designsystem.theme.AslMotion
import com.example.androidstudiolite.designsystem.theme.AslTheme
import com.example.androidstudiolite.feature.editor.engine.EditorSession
import com.example.androidstudiolite.feature.editor.view.AslEditableCodeEditor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.feature.editor.components.EditorBottomPanelContent
import com.example.androidstudiolite.feature.editor.components.EditorDockedPanel
import com.example.androidstudiolite.feature.editor.components.EditorDrawer
import com.example.androidstudiolite.feature.editor.EditorInteraction
import com.example.androidstudiolite.feature.editor.EditorUiState
import com.example.androidstudiolite.feature.editor.EditorViewModel
@Composable
fun EditorRoute(
    projectId: String,
    onCloseProject: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAiAgentSettings: () -> Unit,
    viewModel: EditorViewModel = koinViewModel { parametersOf(projectId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    EditorScreen(
        uiState = uiState,
        onInteraction = { interaction ->
            when (interaction) {
                EditorInteraction.CloseProject -> onCloseProject()
                EditorInteraction.OpenSettings -> onOpenSettings()
                EditorInteraction.OpenAiAgentSettings -> onOpenAiAgentSettings()
                else -> viewModel.onInteraction(interaction)
            }
        },
        sessionFor = viewModel::sessionFor,
        onEdited = viewModel::onSessionEdited,
        onCaretMoved = viewModel::onCaretMoved,
    )
}
@Composable
private fun EditorScreen(
    uiState: EditorUiState,
    onInteraction: (EditorInteraction) -> Unit,
    sessionFor: (String?) -> EditorSession?,
    onEdited: (String) -> Unit,
    onCaretMoved: (Int, Int) -> Unit,
) {
    val colors = AslTheme.colors
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            onInteraction(EditorInteraction.SnackbarShown)
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
            val isTablet = maxWidth >= 600.dp
            val activeTab = uiState.activeTab
            Column(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxWidth().zIndex(2f)) {
                    AslEditorToolbar(
                        projectName = uiState.projectName.ifBlank { "Loading…" },
                    running = uiState.running,
                    onRun = { onInteraction(EditorInteraction.RunProject) },
                    onMenu = { onInteraction(EditorInteraction.ToggleMenu) },
                    actions = {
                        AslIconButton(icon = "undo-2", contentDescription = "Undo", onClick = { onInteraction(EditorInteraction.Undo) })
                        AslIconButton(icon = "redo-2", contentDescription = "Redo", onClick = { onInteraction(EditorInteraction.Redo) })
                        AslIconButton(icon = "save", contentDescription = "Save", onClick = { onInteraction(EditorInteraction.Save) })
                    },
                    overflowItems = listOf(
                        AslOverflowMenuEntry.Item("Find in file", icon = "search", shortcut = "⌘F"),
                        AslOverflowMenuEntry.Item("Reformat code", icon = "align-left"),
                        AslOverflowMenuEntry.Item("Sync Gradle", icon = "refresh-cw"),
                        AslOverflowMenuEntry.Divider,
                        AslOverflowMenuEntry.Item("Simulate build failure", icon = "octagon-alert"),
                        AslOverflowMenuEntry.Item("Simulate memory pressure", icon = "memory-stick"),
                        AslOverflowMenuEntry.Item("Simulate LSP reindex", icon = "loader"),
                        AslOverflowMenuEntry.Divider,
                        AslOverflowMenuEntry.Item("Close project", icon = "x"),
                    ),
                    onOverflowSelect = { item, _ ->
                        when (item.label) {
                            "Find in file" -> onInteraction(EditorInteraction.ToggleFindBar)
                            "Close project" -> onInteraction(EditorInteraction.CloseProject)
                            "Simulate build failure" -> onInteraction(EditorInteraction.SimulateBuildFailure)
                            "Simulate memory pressure" -> onInteraction(EditorInteraction.ToggleMemoryPressure)
                            "Simulate LSP reindex" -> onInteraction(EditorInteraction.SimulateLspReindex)
                        }
                    },
                )
                AslFileTabBar(
                    tabs = uiState.tabs.map { AslFileTab(it.id, it.name, fileIconFor(it.name), it.modified) },
                    activeId = uiState.activeTabId,
                    onSelect = { onInteraction(EditorInteraction.SelectTab(it)) },
                    onClose = { onInteraction(EditorInteraction.CloseTab(it)) },
                )
                if (activeTab != null && !isTablet) {
                    AslBreadcrumbBar(segments = activeTab.breadcrumb)
                }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .imePadding(),
                ) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (isTablet) {
                        EditorDockedPanel(
                            openTool = uiState.openRailTool,
                            fileTree = uiState.fileTree,
                            expandedFolderIds = uiState.expandedFolderIds,
                            selectedFileId = uiState.activeTabId,
                            onSelectTool = { onInteraction(EditorInteraction.SelectRailTool(it)) },
                            onToggleFolder = { onInteraction(EditorInteraction.ToggleFolder(it)) },
                            onSelectFile = { id, name -> onInteraction(EditorInteraction.OpenFile(id, name)) },
                            onDismiss = { onInteraction(EditorInteraction.CloseDrawer) },
                            onOpenSettings = { onInteraction(EditorInteraction.OpenSettings) },
                            onOpenAiAgentSettings = { onInteraction(EditorInteraction.OpenAiAgentSettings) },
                            onCloseProject = { onInteraction(EditorInteraction.CloseProject) },
                            isLoadingFileTree = uiState.isLoadingFileTree,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                    ) {
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
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                )
                            } else {
                                Box(modifier = Modifier.weight(1f).fillMaxSize())
                            }
                        }
                        if (uiState.findBarOpen) {
                            AslFindBar(
                                query = uiState.findQuery,
                                onChange = { onInteraction(EditorInteraction.FindQueryChanged(it)) },
                                matchCount = uiState.findMatchCount,
                                currentMatch = uiState.findCurrentMatch,
                                onNext = { onInteraction(EditorInteraction.FindNext) },
                                onPrev = { onInteraction(EditorInteraction.FindPrevious) },
                                onClose = { onInteraction(EditorInteraction.ToggleFindBar) },
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }
                if (!keyboardOpen) {
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
                            onAction = { onInteraction(EditorInteraction.FreeUpMemory) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onInteraction(EditorInteraction.ToggleMemoryChartExpanded) },
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
                AslBottomToolPanel(
                    tabs = uiState.bottomPanelTabs.map {
                        com.example.androidstudiolite.designsystem.component.navigation.AslBottomPanelTab(it.id, it.label, it.icon, it.count, it.error)
                    },
                    activeId = uiState.activeBottomTabId,
                    expanded = uiState.bottomPanelExpanded,
                    onSelect = { onInteraction(EditorInteraction.SelectBottomTab(it)) },
                    onToggle = { onInteraction(EditorInteraction.ToggleBottomPanel) },
                ) {
                    AslStateCrossfade(targetState = uiState.activeBottomTabId, label = "bottomPanelContent") { tabId ->
                        EditorBottomPanelContent(
                            activeTabId = tabId,
                            running = uiState.running,
                            buildProgressPercent = uiState.buildProgressPercent,
                            buildLines = uiState.buildLines,
                            appLogLines = uiState.appLogLines,
                            onJumpToTab = { onInteraction(EditorInteraction.SelectTab(it)) },
                        )
                    }
                }
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
                        if (uiState.memoryPressureActive) {
                            add(AslStatusBarEntry.Item("${uiState.heapUsedMb} / ${uiState.heapMaxMb} MB", icon = "memory-stick", tone = AslStatusTone.Warning))
                        }
                        when {
                            uiState.running -> add(AslStatusBarEntry.Item("assembleDebug", tone = AslStatusTone.Warning))
                            !uiState.lspUpdating -> add(AslStatusBarEntry.Item("Synced", icon = "check", tone = AslStatusTone.Success))
                        }
                    },
                )
                } else {
                    AslStatusBar(
                        items = buildList {
                            add(AslStatusBarEntry.Item("Ln ${uiState.caretLine + 1}, Col ${uiState.caretColumn + 1}"))
                        },
                    )
                }
                }
            }
            if (!isTablet) {
                EditorDrawer(
                    openTool = uiState.openRailTool,
                    fileTree = uiState.fileTree,
                    expandedFolderIds = uiState.expandedFolderIds,
                    selectedFileId = uiState.activeTabId,
                    onSelectTool = { onInteraction(EditorInteraction.SelectRailTool(it)) },
                    onToggleFolder = { onInteraction(EditorInteraction.ToggleFolder(it)) },
                    onSelectFile = { id, name -> onInteraction(EditorInteraction.OpenFile(id, name)) },
                    onDismiss = { onInteraction(EditorInteraction.CloseDrawer) },
                    onOpenSettings = { onInteraction(EditorInteraction.OpenSettings) },
                    onOpenAiAgentSettings = { onInteraction(EditorInteraction.OpenAiAgentSettings) },
                    onCloseProject = { onInteraction(EditorInteraction.CloseProject) },
                    isLoadingFileTree = uiState.isLoadingFileTree,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
