package com.example.androidstudiolite.feature.editor.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.androidstudiolite.core.designsystem.component.buttons.AslIconButton
import com.example.androidstudiolite.core.designsystem.component.content.AslAutocompletePopup
import com.example.androidstudiolite.core.designsystem.component.content.AslCodeEditor
import com.example.androidstudiolite.core.designsystem.component.content.AslFindBar
import com.example.androidstudiolite.core.designsystem.component.content.AslSuggestion
import com.example.androidstudiolite.core.designsystem.component.content.AslSuggestionKind
import com.example.androidstudiolite.core.designsystem.component.navigation.AslBottomToolPanel
import com.example.androidstudiolite.core.designsystem.component.navigation.AslBreadcrumbBar
import com.example.androidstudiolite.core.designsystem.component.navigation.AslEditorToolbar
import com.example.androidstudiolite.core.designsystem.component.navigation.AslFileTab
import com.example.androidstudiolite.core.designsystem.component.navigation.AslFileTabBar
import com.example.androidstudiolite.core.designsystem.component.navigation.AslStatusBar
import com.example.androidstudiolite.core.designsystem.component.navigation.AslStatusBarEntry
import com.example.androidstudiolite.core.designsystem.component.navigation.AslStatusTone
import com.example.androidstudiolite.core.designsystem.component.buttons.AslOverflowMenuEntry
import com.example.androidstudiolite.core.designsystem.component.feedback.AslBanner
import com.example.androidstudiolite.core.designsystem.component.feedback.AslBannerTone
import com.example.androidstudiolite.core.designsystem.component.feedback.AslLinearProgress
import com.example.androidstudiolite.core.designsystem.component.ide.AslMemoryChartMini
import com.example.androidstudiolite.core.designsystem.component.ide.AslMemoryChartTone
import com.example.androidstudiolite.core.designsystem.theme.AslTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.feature.editor.components.EditorBottomPanelContent
import com.example.androidstudiolite.feature.editor.components.EditorDockedPanel
import com.example.androidstudiolite.feature.editor.components.EditorDrawer
import com.example.androidstudiolite.feature.editor.interaction.EditorInteraction
import com.example.androidstudiolite.feature.editor.uiState.EditorUiState
import com.example.androidstudiolite.feature.editor.viewModel.EditorViewModel

private val AUTOCOMPLETE_DEMO_SUGGESTIONS = listOf(
    AslSuggestion(label = "setContent", detail = "(content: @Composable () -> Unit)", type = "Unit", kind = AslSuggestionKind.Method),
    AslSuggestion(label = "savedInstanceState", type = "Bundle?", kind = AslSuggestionKind.Field),
    AslSuggestion(label = "ComponentActivity", type = "class", kind = AslSuggestionKind.Class),
    AslSuggestion(label = "override", kind = AslSuggestionKind.Keyword),
    AslSuggestion(label = "Log.d(TAG, …)", detail = "logging snippet", kind = AslSuggestionKind.Snippet),
)

@Composable
fun EditorRoute(
    projectId: String,
    onCloseProject: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAiAgentSettings: () -> Unit,
    viewModel: EditorViewModel = viewModel { EditorViewModel(projectId) },
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
    )
}

@Composable
private fun EditorScreen(
    uiState: EditorUiState,
    onInteraction: (EditorInteraction) -> Unit,
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
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val isTablet = maxWidth >= 600.dp
            Column(modifier = Modifier.fillMaxSize()) {
                AslEditorToolbar(
                    projectName = uiState.projectName.ifBlank { "Loading…" },
                    running = uiState.running,
                    onRun = { onInteraction(EditorInteraction.RunProject) },
                    onMenu = { onInteraction(EditorInteraction.ToggleMenu) },
                    actions = {
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
                        AslOverflowMenuEntry.Item("Show autocomplete demo", icon = "sparkles"),
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
                            "Show autocomplete demo" -> onInteraction(EditorInteraction.ToggleAutocompleteDemo)
                        }
                    },
                )
                AslFileTabBar(
                    tabs = uiState.tabs.map { AslFileTab(it.id, it.name, it.icon, it.modified) },
                    activeId = uiState.activeTabId,
                    onSelect = { onInteraction(EditorInteraction.SelectTab(it)) },
                    onClose = { onInteraction(EditorInteraction.CloseTab(it)) },
                )
                val activeTab = uiState.activeTab
                if (activeTab != null && !isTablet) {
                    AslBreadcrumbBar(segments = activeTab.breadcrumb)
                }
                Row(modifier = Modifier.weight(1f).fillMaxSize()) {
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
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (activeTab != null && isTablet) {
                                AslBreadcrumbBar(segments = activeTab.breadcrumb)
                            }
                            if (activeTab != null) {
                                AslCodeEditor(
                                    lines = activeTab.lines,
                                    modifier = Modifier.weight(1f).fillMaxSize(),
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
                        if (uiState.autocompletePopupVisible) {
                            AslAutocompletePopup(
                                suggestions = AUTOCOMPLETE_DEMO_SUGGESTIONS,
                                modifier = Modifier.padding(start = 48.dp, top = 96.dp),
                                onSelect = { _, _ -> onInteraction(EditorInteraction.ToggleAutocompleteDemo) },
                            )
                        }
                    }
                }
                if (uiState.memoryPressureActive) {
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
                        if (uiState.memoryChartExpanded) {
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
                if (uiState.lspUpdating) {
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
                        com.example.androidstudiolite.core.designsystem.component.navigation.AslBottomPanelTab(it.id, it.label, it.icon, it.count, it.error)
                    },
                    activeId = uiState.activeBottomTabId,
                    expanded = uiState.bottomPanelExpanded,
                    onSelect = { onInteraction(EditorInteraction.SelectBottomTab(it)) },
                    onToggle = { onInteraction(EditorInteraction.ToggleBottomPanel) },
                ) {
                    EditorBottomPanelContent(
                        activeTabId = uiState.activeBottomTabId,
                        running = uiState.running,
                        buildProgressPercent = uiState.buildProgressPercent,
                        buildLines = uiState.buildLines,
                        appLogLines = uiState.appLogLines,
                        onJumpToTab = { onInteraction(EditorInteraction.SelectTab(it)) },
                    )
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
                        add(AslStatusBarEntry.Item("Ln 42, Col 18"))
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
