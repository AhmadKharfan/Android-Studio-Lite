package com.example.androidstudiolite.feature.editor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.designsystem.component.content.AslCodeLine
import com.example.androidstudiolite.designsystem.component.content.AslCodeSpan
import com.example.androidstudiolite.designsystem.component.content.AslLineGit
import com.example.androidstudiolite.designsystem.component.content.AslLogLevel
import com.example.androidstudiolite.designsystem.component.content.AslSyntaxColor
import com.example.androidstudiolite.domain.model.FileNode
import com.example.androidstudiolite.domain.repository.FileTreeRepository
import com.example.androidstudiolite.domain.repository.ProjectRepository
import com.example.androidstudiolite.feature.editor.EditorInteraction
import com.example.androidstudiolite.feature.editor.AppLogLineUiModel
import com.example.androidstudiolite.feature.editor.BottomPanelTabUiModel
import com.example.androidstudiolite.feature.editor.BuildOutputLineUiModel
import com.example.androidstudiolite.feature.editor.EditorFileNodeUiModel
import com.example.androidstudiolite.feature.editor.EditorRailTool
import com.example.androidstudiolite.feature.editor.EditorTabUiModel
import com.example.androidstudiolite.feature.editor.EditorUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val MAIN_ACTIVITY_TAB = EditorTabUiModel(
    id = "MainActivity.kt",
    name = "MainActivity.kt",
    icon = "file-code",
    modified = true,
    breadcrumb = listOf("app", "src", "main", "java", "MainActivity.kt"),
    lines = listOf(
        AslCodeLine(listOf(AslCodeSpan("// Entry point", AslSyntaxColor.Comment))),
        AslCodeLine(
            listOf(
                AslCodeSpan("class ", AslSyntaxColor.Keyword),
                AslCodeSpan("MainActivity", AslSyntaxColor.Type),
                AslCodeSpan(" : "),
                AslCodeSpan("ComponentActivity", AslSyntaxColor.Type),
                AslCodeSpan("() {"),
            ),
        ),
        AslCodeLine(
            listOf(
                AslCodeSpan("  override fun ", AslSyntaxColor.Keyword),
                AslCodeSpan("onCreate", AslSyntaxColor.Function),
                AslCodeSpan("(state: "),
                AslCodeSpan("Bundle", AslSyntaxColor.Type),
                AslCodeSpan("?) {"),
            ),
            git = AslLineGit.Modified,
        ),
        AslCodeLine(
            listOf(AslCodeSpan("    super.", AslSyntaxColor.Keyword), AslCodeSpan("onCreate", AslSyntaxColor.Function), AslCodeSpan("(state)")),
            git = AslLineGit.Modified,
        ),
        AslCodeLine(
            listOf(
                AslCodeSpan("    val ", AslSyntaxColor.Keyword),
                AslCodeSpan("greeting", AslSyntaxColor.Variable),
                AslCodeSpan(" = "),
                AslCodeSpan("\"Hello, Lite!\"", AslSyntaxColor.StringLiteral),
            ),
            git = AslLineGit.Added,
        ),
        AslCodeLine(listOf(AslCodeSpan("    setContent", AslSyntaxColor.Function)), active = true, git = AslLineGit.Added),
        AslCodeLine(listOf(AslCodeSpan("  }"))),
        AslCodeLine(listOf(AslCodeSpan("}"))),
    ),
)

private val GRADLE_TAB = EditorTabUiModel(
    id = "build.gradle.kts",
    name = "build.gradle.kts",
    icon = "file-cog",
    modified = false,
    breadcrumb = listOf("app", "build.gradle.kts"),
    lines = listOf(
        AslCodeLine(listOf(AslCodeSpan("plugins {", AslSyntaxColor.Variable))),
        AslCodeLine(listOf(AslCodeSpan("    id(", AslSyntaxColor.Function), AslCodeSpan("\"com.android.application\"", AslSyntaxColor.StringLiteral), AslCodeSpan(")"))),
        AslCodeLine(listOf(AslCodeSpan("}"))),
    ),
)

private val BOTTOM_PANEL_TABS = listOf(
    BottomPanelTabUiModel("build", "Build Output", "hammer"),
    BottomPanelTabUiModel("logs", "App Logs", "scroll-text"),
    BottomPanelTabUiModel("term", "Terminal", "terminal"),
    BottomPanelTabUiModel("diag", "Diagnostics", "stethoscope", count = 2, error = true),
)

private val SUCCESS_TASKS = listOf(
    BuildOutputLineUiModel("> Task :app:preBuild", status = "success", duration = "0.1s"),
    BuildOutputLineUiModel("> Task :app:mergeDebugResources", status = "success", duration = "1.8s"),
    BuildOutputLineUiModel("> Task :app:compileDebugKotlin", status = "success", duration = "4.1s"),
    BuildOutputLineUiModel("w: unused variable 'x'", depth = 1, status = "skipped"),
    BuildOutputLineUiModel("> Task :app:dexBuilderDebug", status = "success", duration = "0.9s"),
    BuildOutputLineUiModel("> Task :app:packageDebug", status = "success", duration = "0.6s"),
    BuildOutputLineUiModel("> Task :app:installDebug", status = "success", duration = "1.1s"),
)

private val FAILING_TASKS = listOf(
    BuildOutputLineUiModel("> Task :app:preBuild", status = "success", duration = "0.1s"),
    BuildOutputLineUiModel("> Task :app:compileDebugKotlin", status = "failed", duration = "3.9s"),
    BuildOutputLineUiModel("e: MainActivity.kt:39:11 unresolved reference: fooBar", depth = 1, status = "failed", jumpToTabId = "MainActivity.kt"),
    BuildOutputLineUiModel("> Task :app:packageDebug", status = "skipped"),
    BuildOutputLineUiModel("BUILD FAILED in 6s · 2 errors", status = "failed"),
)

private val APP_LOG_TEMPLATE = listOf(
    Triple(AslLogLevel.DEBUG, "ActivityTaskManager", "Displayed com.example.myapplication/.MainActivity in 312ms"),
    Triple(AslLogLevel.INFO, "Choreographer", "Skipped 2 frames! The application may be doing too much work on its main thread."),
    Triple(AslLogLevel.DEBUG, "MainActivity", "onCreate() called"),
    Triple(AslLogLevel.INFO, "ViewRootImpl", "relayoutWindow returned early"),
    Triple(AslLogLevel.WARN, "ResourcesCompat", "Failed to find id 0x7f0a001a; resource IDs starting with 0x00 are reserved for system use."),
)

class EditorViewModel(
    projectId: String,
    private val projectRepository: ProjectRepository,
    private val fileTreeRepository: FileTreeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        EditorUiState(tabs = listOf(MAIN_ACTIVITY_TAB, GRADLE_TAB), activeTabId = MAIN_ACTIVITY_TAB.id, bottomPanelTabs = BOTTOM_PANEL_TABS),
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val project = projectRepository.openProject(projectId)
            val tree = fileTreeRepository.getFileTree(projectId).map { it.toUiModel() }
            delay(300)
            _uiState.value = _uiState.value.copy(
                projectName = project.name,
                fileTree = tree,
                expandedFolderIds = setOf("app", "app/src", "app/src/main", "app/src/main/java"),
                isLoadingFileTree = false,
            )
        }
    }

    fun onInteraction(interaction: EditorInteraction) {
        val state = _uiState.value
        when (interaction) {
            is EditorInteraction.SelectTab -> _uiState.value = state.copy(activeTabId = interaction.id)
            is EditorInteraction.CloseTab -> closeTab(interaction.id)
            EditorInteraction.ToggleMenu -> _uiState.value = state.copy(
                openRailTool = if (state.openRailTool != null) null else EditorRailTool.Files,
            )
            is EditorInteraction.SelectRailTool -> _uiState.value = state.copy(
                openRailTool = if (state.openRailTool == interaction.tool) null else interaction.tool,
            )
            EditorInteraction.CloseDrawer -> _uiState.value = state.copy(openRailTool = null)
            is EditorInteraction.ToggleFolder -> _uiState.value = state.copy(
                expandedFolderIds = if (interaction.id in state.expandedFolderIds) state.expandedFolderIds - interaction.id else state.expandedFolderIds + interaction.id,
            )
            is EditorInteraction.OpenFile -> openFile(interaction.id, interaction.name)
            EditorInteraction.RunProject -> runProject()
            EditorInteraction.SimulateBuildFailure -> runProject(simulateFailure = true)
            is EditorInteraction.SelectBottomTab -> _uiState.value = state.copy(activeBottomTabId = interaction.id, bottomPanelExpanded = true)
            EditorInteraction.ToggleBottomPanel -> _uiState.value = state.copy(bottomPanelExpanded = !state.bottomPanelExpanded)
            EditorInteraction.CloseProject, EditorInteraction.OpenSettings, EditorInteraction.OpenAiAgentSettings, EditorInteraction.Save -> Unit
            EditorInteraction.SnackbarShown -> _uiState.value = state.copy(snackbarMessage = null)
            EditorInteraction.ToggleMemoryPressure -> _uiState.value = state.copy(
                memoryPressureActive = !state.memoryPressureActive,
                memoryChartExpanded = false,
            )
            EditorInteraction.ToggleMemoryChartExpanded -> _uiState.value = state.copy(memoryChartExpanded = !state.memoryChartExpanded)
            EditorInteraction.FreeUpMemory -> freeUpMemory()
            EditorInteraction.SimulateLspReindex -> simulateLspReindex()
            EditorInteraction.ToggleFindBar -> _uiState.value = state.copy(
                findBarOpen = !state.findBarOpen,
                findQuery = "",
                findMatchCount = 0,
                findCurrentMatch = 0,
            )
            is EditorInteraction.FindQueryChanged -> updateFindQuery(interaction.query)
            EditorInteraction.FindNext -> _uiState.value = state.copy(
                findCurrentMatch = if (state.findMatchCount == 0) 0 else (state.findCurrentMatch % state.findMatchCount) + 1,
            )
            EditorInteraction.FindPrevious -> _uiState.value = state.copy(
                findCurrentMatch = if (state.findMatchCount == 0) 0 else ((state.findCurrentMatch - 2 + state.findMatchCount) % state.findMatchCount) + 1,
            )
            EditorInteraction.ToggleAutocompleteDemo -> _uiState.value = state.copy(autocompletePopupVisible = !state.autocompletePopupVisible)
        }
    }

    private fun updateFindQuery(query: String) {
        val state = _uiState.value
        val text = state.activeTab?.lines?.joinToString("\n") { line -> line.spans.joinToString("") { it.text } }.orEmpty()
        val count = if (query.isEmpty()) 0 else countOccurrences(text, query)
        _uiState.value = state.copy(
            findQuery = query,
            findMatchCount = count,
            findCurrentMatch = if (count > 0) 1 else 0,
        )
    }

    private fun countOccurrences(text: String, query: String): Int {
        var count = 0
        var index = text.indexOf(query, ignoreCase = true)
        while (index >= 0) {
            count++
            index = text.indexOf(query, index + 1, ignoreCase = true)
        }
        return count
    }

    private fun freeUpMemory() {
        val state = _uiState.value
        val kept = state.tabs.filter { it.id == state.activeTabId }
        _uiState.value = state.copy(
            tabs = kept,
            memoryPressureActive = false,
            memoryChartExpanded = false,
        )
    }

    private fun simulateLspReindex() {
        if (_uiState.value.lspUpdating) return
        _uiState.value = _uiState.value.copy(lspUpdating = true)
        viewModelScope.launch {
            delay(2500)
            _uiState.value = _uiState.value.copy(lspUpdating = false)
        }
    }

    private fun closeTab(id: String) {
        val state = _uiState.value
        val remaining = state.tabs.filterNot { it.id == id }
        _uiState.value = state.copy(
            tabs = remaining,
            activeTabId = if (state.activeTabId == id) remaining.lastOrNull()?.id else state.activeTabId,
        )
    }

    private fun openFile(id: String, name: String) {
        val state = _uiState.value
        val existing = state.tabs.firstOrNull { it.id == id }
        if (existing == null) {
            val placeholder = EditorTabUiModel(
                id = id,
                name = name,
                icon = "file-code",
                modified = false,
                breadcrumb = listOf(name),
                lines = listOf(AslCodeLine(listOf(AslCodeSpan("// $name", AslSyntaxColor.Comment)))),
            )
            _uiState.value = state.copy(tabs = state.tabs + placeholder, activeTabId = id, openRailTool = null)
        } else {
            _uiState.value = state.copy(activeTabId = id, openRailTool = null)
        }
    }

    private fun runProject(simulateFailure: Boolean = false) {
        if (_uiState.value.running) return
        _uiState.value = _uiState.value.copy(
            running = true,
            bottomPanelExpanded = true,
            activeBottomTabId = "build",
            buildLines = emptyList(),
            buildProgressPercent = 0,
            buildFailed = false,
        )
        viewModelScope.launch {
            val tasks = if (simulateFailure) FAILING_TASKS else SUCCESS_TASKS
            for ((index, task) in tasks.withIndex()) {
                if (task.duration != null) {
                    _uiState.value = _uiState.value.copy(buildLines = _uiState.value.buildLines + task.copy(status = "running", duration = null))
                    delay(300)
                    _uiState.value = _uiState.value.copy(buildLines = _uiState.value.buildLines.dropLast(1) + task)
                } else {
                    delay(300)
                    _uiState.value = _uiState.value.copy(buildLines = _uiState.value.buildLines + task)
                }
                _uiState.value = _uiState.value.copy(buildProgressPercent = ((index + 1) * 100) / tasks.size)
            }
            _uiState.value = _uiState.value.copy(
                running = false,
                buildFailed = simulateFailure,
                snackbarMessage = if (simulateFailure) null else "APK installed on device",
                bottomPanelTabs = _uiState.value.bottomPanelTabs.map {
                    if (it.id == "build") it.copy(count = if (simulateFailure) 2 else null, error = simulateFailure) else it
                },
            )
            if (!simulateFailure) {
                emitAppLogs()
            }
        }
    }

    private suspend fun emitAppLogs() {
        _uiState.value = _uiState.value.copy(appLogLines = emptyList())
        val timeFormat = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
        for ((level, tag, message) in APP_LOG_TEMPLATE) {
            delay(220)
            val line = AppLogLineUiModel(time = timeFormat.format(java.util.Date()), level = level, tag = tag, message = message)
            _uiState.value = _uiState.value.copy(appLogLines = _uiState.value.appLogLines + line)
        }
    }

    private fun FileNode.toUiModel(): EditorFileNodeUiModel = EditorFileNodeUiModel(
        id = id,
        name = name,
        children = children?.map { it.toUiModel() },
        gitStatus = gitStatus,
    )
}
