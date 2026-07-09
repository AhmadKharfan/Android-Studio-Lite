package com.example.androidstudiolite.feature.editor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.designsystem.component.content.AslLogLevel
import com.example.androidstudiolite.domain.model.FileNode
import com.example.androidstudiolite.domain.repository.FileContentRepository
import com.example.androidstudiolite.domain.repository.FileTreeRepository
import com.example.androidstudiolite.domain.repository.PreferencesRepository
import com.example.androidstudiolite.domain.repository.ProjectRepository
import com.example.androidstudiolite.feature.editor.engine.EditorLanguage
import com.example.androidstudiolite.feature.editor.engine.EditorSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
private const val DEFAULT_OPEN_FILE = "MainActivity.kt"
private const val AUTO_SAVE_DEBOUNCE_MS = 2000L
class EditorViewModel(
    projectId: String,
    private val projectRepository: ProjectRepository,
    private val fileTreeRepository: FileTreeRepository,
    private val fileContentRepository: FileContentRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        EditorUiState(bottomPanelTabs = BOTTOM_PANEL_TABS),
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()
    private val sessions = mutableMapOf<String, EditorSession>()
    private var autoSaveEnabled = true
    private var autoSaveJob: Job? = null
    init {
        viewModelScope.launch {
            preferencesRepository.observePreferences().collect { prefs ->
                autoSaveEnabled = prefs.editorAutoSave
                _uiState.value = _uiState.value.copy(
                    editorFontSize = prefs.editorFontSize,
                    editorTabSize = prefs.editorTabSize,
                    editorThemeId = prefs.editorThemeId,
                    kotlinLspEnabled = prefs.kotlinLspEnabled,
                    javaLspEnabled = prefs.javaLspEnabled,
                    xmlLspEnabled = prefs.xmlLspEnabled,
                )
            }
        }
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
            openFile(DEFAULT_OPEN_FILE, DEFAULT_OPEN_FILE)
        }
    }
    fun sessionFor(id: String?): EditorSession? = id?.let { sessions[it] }
    fun onInteraction(interaction: EditorInteraction) {
        val state = _uiState.value
        when (interaction) {
            is EditorInteraction.SelectTab -> {
                _uiState.value = state.copy(activeTabId = interaction.id)
            }
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
            EditorInteraction.Save -> saveActiveTab()
            EditorInteraction.Undo -> undoRedoActive { it.undo() }
            EditorInteraction.Redo -> undoRedoActive { it.redo() }
            EditorInteraction.CloseProject, EditorInteraction.OpenSettings, EditorInteraction.OpenAiAgentSettings -> Unit
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
    fun onSessionEdited(id: String) {
        val session = sessions[id] ?: return
        val newText = session.text
        _uiState.value = _uiState.value.copy(
            tabs = _uiState.value.tabs.map {
                if (it.id == id) it.copy(text = newText, modified = true) else it
            },
        )
        scheduleAutoSave(id)
    }
    private fun scheduleAutoSave(id: String) {
        if (!autoSaveEnabled) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(AUTO_SAVE_DEBOUNCE_MS)
            val session = sessions[id] ?: return@launch
            fileContentRepository.writeText(id, session.text)
            _uiState.value = _uiState.value.copy(
                tabs = _uiState.value.tabs.map { if (it.id == id) it.copy(modified = false) else it },
            )
        }
    }
    private fun undoRedoActive(action: (EditorSession) -> Boolean) {
        val id = _uiState.value.activeTabId ?: return
        val session = sessions[id] ?: return
        if (!action(session)) return
        val caret = session.caretPosition
        _uiState.value = _uiState.value.copy(
            tabs = _uiState.value.tabs.map { if (it.id == id) it.copy(text = session.text, modified = true) else it },
            caretLine = caret.line,
            caretColumn = caret.column,
        )
    }
    fun onCaretMoved(line: Int, column: Int) {
        val state = _uiState.value
        if (state.caretLine == line && state.caretColumn == column) return
        _uiState.value = state.copy(caretLine = line, caretColumn = column)
    }
    private fun saveActiveTab() {
        val id = _uiState.value.activeTabId ?: return
        val session = sessions[id] ?: return
        val text = session.text
        viewModelScope.launch {
            fileContentRepository.writeText(id, text)
            _uiState.value = _uiState.value.copy(
                tabs = _uiState.value.tabs.map { if (it.id == id) it.copy(modified = false) else it },
                snackbarMessage = "Saved ${_uiState.value.tabs.firstOrNull { it.id == id }?.name ?: id}",
            )
        }
    }
    private fun updateFindQuery(query: String) {
        val state = _uiState.value
        val text = state.activeTab?.text.orEmpty()
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
        val keptIds = kept.map { it.id }.toSet()
        sessions.keys.retainAll(keptIds)
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
            delay(800)
            _uiState.value = _uiState.value.copy(lspUpdating = false)
        }
    }
    private fun closeTab(id: String) {
        val state = _uiState.value
        sessions.remove(id)
        val remaining = state.tabs.filterNot { it.id == id }
        _uiState.value = state.copy(
            tabs = remaining,
            activeTabId = if (state.activeTabId == id) remaining.lastOrNull()?.id else state.activeTabId,
        )
    }
    private fun openFile(id: String, name: String) {
        val state = _uiState.value
        if (state.tabs.any { it.id == id }) {
            _uiState.value = state.copy(activeTabId = id, openRailTool = null)
            return
        }
        viewModelScope.launch {
            val content = fileContentRepository.readText(id)
            val language = EditorLanguage.fromFileName(name)
            sessions[id] = EditorSession(content, language)
            val tab = EditorTabUiModel(
                id = id,
                name = name,
                text = content,
                language = language,
                modified = false,
                breadcrumb = breadcrumbFor(id, name),
            )
            val current = _uiState.value
            if (current.tabs.any { it.id == id }) {
                _uiState.value = current.copy(activeTabId = id, openRailTool = null)
            } else {
                _uiState.value = current.copy(tabs = current.tabs + tab, activeTabId = id, openRailTool = null)
            }
        }
    }
    private fun breadcrumbFor(id: String, name: String): List<String> {
        val parts = id.split('/').filter { it.isNotEmpty() }
        return if (parts.size > 1) parts else listOf(name)
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
