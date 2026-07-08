package com.example.androidstudiolite.feature.editor.uiState

import com.example.androidstudiolite.core.designsystem.component.content.AslCodeLine
import com.example.androidstudiolite.core.designsystem.component.content.AslLogLevel
import com.example.androidstudiolite.domain.model.GitFileStatus

data class EditorTabUiModel(
    val id: String,
    val name: String,
    val icon: String,
    val modified: Boolean,
    val breadcrumb: List<String>,
    val lines: List<AslCodeLine>,
)

data class EditorFileNodeUiModel(
    val id: String,
    val name: String,
    val children: List<EditorFileNodeUiModel>? = null,
    val icon: String? = null,
    val gitStatus: GitFileStatus? = null,
)

enum class EditorRailTool { Files, Git, AiAgent, Variants, Assets }

data class BottomPanelTabUiModel(
    val id: String,
    val label: String,
    val icon: String,
    val count: Int? = null,
    val error: Boolean = false,
)

data class BuildOutputLineUiModel(
    val text: String,
    val depth: Int = 0,
    val status: String? = null,
    val duration: String? = null,
    val jumpToTabId: String? = null,
)

data class AppLogLineUiModel(
    val time: String,
    val level: AslLogLevel,
    val tag: String?,
    val message: String,
)

data class EditorUiState(
    val projectName: String = "",
    val tabs: List<EditorTabUiModel> = emptyList(),
    val activeTabId: String? = null,
    val running: Boolean = false,
    val openRailTool: EditorRailTool? = null,
    val fileTree: List<EditorFileNodeUiModel> = emptyList(),
    val expandedFolderIds: Set<String> = emptySet(),
    val bottomPanelExpanded: Boolean = false,
    val bottomPanelTabs: List<BottomPanelTabUiModel> = emptyList(),
    val activeBottomTabId: String = "build",
    val buildProgressPercent: Int? = null,
    val buildLines: List<BuildOutputLineUiModel> = emptyList(),
    val buildFailed: Boolean = false,
    val snackbarMessage: String? = null,
    val memoryPressureActive: Boolean = false,
    val memoryChartExpanded: Boolean = false,
    val heapUsedMb: Int = 489,
    val heapMaxMb: Int = 512,
    val heapSeries: List<Int> = listOf(300, 340, 390, 420, 455, 470, 489),
    val lspUpdating: Boolean = false,
    val findBarOpen: Boolean = false,
    val findQuery: String = "",
    val findMatchCount: Int = 0,
    val findCurrentMatch: Int = 0,
    val appLogLines: List<AppLogLineUiModel> = emptyList(),
    val isLoadingFileTree: Boolean = true,
    val autocompletePopupVisible: Boolean = false,
) {
    val activeTab: EditorTabUiModel?
        get() = tabs.firstOrNull { it.id == activeTabId }
}
