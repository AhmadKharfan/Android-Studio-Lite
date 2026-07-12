package com.ahmadkharfan.androidstudiolite.feature.editor
import androidx.lifecycle.viewModelScope
import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslLogLevel
import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeEvent
import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeType
import com.ahmadkharfan.androidstudiolite.domain.model.FileNode
import com.ahmadkharfan.androidstudiolite.domain.repository.FileContentRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.FileTreeRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.PreferencesRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorLanguage
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
private val BOTTOM_PANEL_TABS = listOf(
    BottomPanelTabUiModel("build", "Build Output", "hammer"),
    BottomPanelTabUiModel("logs", "App Logs", "scroll-text"),
    BottomPanelTabUiModel("term", "Terminal", "terminal"),
    BottomPanelTabUiModel("diag", "Diagnostics", "stethoscope"),
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
private const val AUTO_SAVE_DEBOUNCE_MS = 2000L
private val CODE_FILE_EXTENSIONS = setOf("kt", "kts", "java", "xml", "gradle")
class EditorViewModel(
    projectId: String,
    private val projectRepository: ProjectRepository,
    private val fileTreeRepository: FileTreeRepository,
    private val fileContentRepository: FileContentRepository,
    private val preferencesRepository: PreferencesRepository,
) : BaseViewModel<EditorUiState, EditorEffect>(
    initialState = EditorUiState(bottomPanelTabs = BOTTOM_PANEL_TABS),
), EditorInteractionListener {
    private val sessions = mutableMapOf<String, EditorSession>()
    private var autoSaveEnabled = true
    private var autoSaveJob: Job? = null

    /** Absolute path of the open project's root, used to show file-tree paths relative to it. */
    private var projectRootPath: String? = null
    init {
        tryToCollect(
            block = { preferencesRepository.observePreferences() },
            onCollect = { prefs ->
                autoSaveEnabled = prefs.editorAutoSave
                updateState {
                    copy(
                        editorFontSize = prefs.editorFontSize,
                        editorTabSize = prefs.editorTabSize,
                        editorThemeId = prefs.editorThemeId,
                        kotlinLspEnabled = prefs.kotlinLspEnabled,
                        javaLspEnabled = prefs.javaLspEnabled,
                        xmlLspEnabled = prefs.xmlLspEnabled,
                    )
                }
            },
        )
        tryToExecute(
            block = {
                val project = projectRepository.openProject(projectId)
                val nodes = fileTreeRepository.getFileTree(projectId)
                Triple(project.name, project.path, nodes)
            },
            onSuccess = { (projectName, projectPath, nodes) ->
                projectRootPath = projectPath
                updateState {
                    copy(
                        projectName = projectName,
                        fileTree = nodes.map { it.toUiModel() },
                        expandedFolderIds = defaultExpandedIds(nodes),
                        isLoadingFileTree = false,
                    )
                }
                firstOpenableFile(nodes)?.let { openFile(it.id, it.name) }
            },
        )
        // Detect edits to open files made outside the editor (e.g. by another repository operation).
        tryToCollect(
            block = { fileContentRepository.observeChanges() },
            onCollect = { event -> onExternalFileEvent(event) },
        )
    }

    /**
     * Directories to expand initially: every top-level directory, plus every directory on the path to
     * the file we open by default, so the user lands on real source without hand-expanding the tree.
     */
    private fun defaultExpandedIds(nodes: List<FileNode>): Set<String> {
        val topLevelDirs = nodes.filter { it.children != null }.map { it.id }
        val trailDirs = pathToFile(nodes) { isCodeFile(it.name) }?.dropLast(1)?.map { it.id }.orEmpty()
        return (topLevelDirs + trailDirs).toSet()
    }

    /** The first source file (preferred), else the first file of any kind, or null for an empty project. */
    private fun firstOpenableFile(nodes: List<FileNode>): FileNode? =
        (pathToFile(nodes) { isCodeFile(it.name) } ?: pathToFile(nodes) { true })?.lastOrNull()

    /** Depth-first search returning the chain of nodes (ancestor dirs … file) to the first matching file. */
    private fun pathToFile(
        nodes: List<FileNode>,
        trail: List<FileNode> = emptyList(),
        predicate: (FileNode) -> Boolean,
    ): List<FileNode>? {
        for (node in nodes) {
            val nextTrail = trail + node
            if (node.children == null) {
                if (predicate(node)) return nextTrail
            } else {
                pathToFile(node.children, nextTrail, predicate)?.let { return it }
            }
        }
        return null
    }

    private fun isCodeFile(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in CODE_FILE_EXTENSIONS

    private suspend fun onExternalFileEvent(event: FileChangeEvent) {
        if (event.type != FileChangeType.MODIFIED) return
        val tab = state.value.tabs.firstOrNull { it.id == event.path } ?: return
        val session = sessions[event.path] ?: return
        val onDisk = try {
            fileContentRepository.readText(event.path)
        } catch (_: Throwable) {
            return
        }
        // Equal means it was our own save (or a no-op write) — nothing to warn about.
        if (onDisk == session.text) return
        updateState { copy(snackbarMessage = "‘${tab.name}’ changed on disk outside the editor") }
    }
    fun sessionFor(id: String?): EditorSession? = id?.let { sessions[it] }

    override fun onSelectTab(id: String) {
        updateState { copy(activeTabId = id, activeDiagnostics = emptyList()) }
    }
    override fun onCloseTab(id: String) = closeTab(id)
    override fun onToggleMenu() {
        updateState { copy(openRailTool = if (openRailTool != null) null else EditorRailTool.Files) }
    }
    override fun onSelectRailTool(tool: EditorRailTool) {
        updateState { copy(openRailTool = if (openRailTool == tool) null else tool) }
    }
    override fun onCloseDrawer() {
        updateState { copy(openRailTool = null) }
    }
    override fun onToggleFolder(id: String) {
        updateState { copy(expandedFolderIds = if (id in expandedFolderIds) expandedFolderIds - id else expandedFolderIds + id) }
    }
    override fun onOpenFile(id: String, name: String) = openFile(id, name)
    override fun onRunProject() = runProject()
    override fun onSimulateBuildFailure() = runProject(simulateFailure = true)
    override fun onSelectBottomTab(id: String) {
        updateState { copy(activeBottomTabId = id, bottomPanelExpanded = true) }
    }
    override fun onToggleBottomPanel() {
        updateState { copy(bottomPanelExpanded = !bottomPanelExpanded) }
    }
    override fun onSave() = saveActiveTab()
    override fun onUndo() = undoRedoActive { it.undo() }
    override fun onRedo() = undoRedoActive { it.redo() }
    override fun onCloseProject() {
        emitEffect(EditorEffect.CloseProject)
    }
    override fun onOpenSettings() {
        emitEffect(EditorEffect.OpenSettings)
    }
    override fun onOpenAiAgentSettings() {
        emitEffect(EditorEffect.OpenAiAgentSettings)
    }
    override fun onSnackbarShown() {
        updateState { copy(snackbarMessage = null) }
    }
    override fun onToggleMemoryPressure() {
        updateState { copy(memoryPressureActive = !memoryPressureActive, memoryChartExpanded = false) }
    }
    override fun onToggleMemoryChartExpanded() {
        updateState { copy(memoryChartExpanded = !memoryChartExpanded) }
    }
    override fun onFreeUpMemory() = freeUpMemory()
    override fun onSimulateLspReindex() = simulateLspReindex()
    override fun onToggleFindBar() {
        updateState { copy(findBarOpen = !findBarOpen, findQuery = "", findMatchCount = 0, findCurrentMatch = 0) }
    }
    override fun onFindQueryChanged(query: String) = updateFindQuery(query)
    override fun onFindNext() {
        updateState { copy(findCurrentMatch = if (findMatchCount == 0) 0 else (findCurrentMatch % findMatchCount) + 1) }
    }
    override fun onFindPrevious() {
        updateState {
            copy(findCurrentMatch = if (findMatchCount == 0) 0 else ((findCurrentMatch - 2 + findMatchCount) % findMatchCount) + 1)
        }
    }
    override fun onToggleAutocompleteDemo() {
        updateState { copy(autocompletePopupVisible = !autocompletePopupVisible) }
    }
    override fun onJumpToDiagnostic(diagnostic: DiagnosticUiModel) = jumpToDiagnostic(diagnostic)

    fun onSessionEdited(id: String) {
        val session = sessions[id] ?: return
        val newText = session.text
        updateState {
            copy(tabs = tabs.map { if (it.id == id) it.copy(text = newText, modified = true) else it })
        }
        scheduleAutoSave(id)
    }
    private fun scheduleAutoSave(id: String) {
        if (!autoSaveEnabled) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(AUTO_SAVE_DEBOUNCE_MS)
            val session = sessions[id] ?: return@launch
            fileContentRepository.writeText(id, session.text)
            updateState { copy(tabs = tabs.map { if (it.id == id) it.copy(modified = false) else it }) }
        }
    }
    private fun undoRedoActive(action: (EditorSession) -> Boolean) {
        val id = state.value.activeTabId ?: return
        val session = sessions[id] ?: return
        if (!action(session)) return
        val caret = session.caretPosition
        updateState {
            copy(
                tabs = tabs.map { if (it.id == id) it.copy(text = session.text, modified = true) else it },
                caretLine = caret.line,
                caretColumn = caret.column,
            )
        }
    }
    fun onDiagnostics(id: String, diagnostics: List<com.ahmadkharfan.androidstudiolite.feature.editor.engine.Diagnostic>) {
        if (id != state.value.activeTabId) return
        val errors = diagnostics.count { it.severity == com.ahmadkharfan.androidstudiolite.feature.editor.engine.DiagnosticSeverity.Error }
        val warnings = diagnostics.count { it.severity == com.ahmadkharfan.androidstudiolite.feature.editor.engine.DiagnosticSeverity.Warning }
        val problems = errors + warnings
        val session = sessions[id]
        val ui = diagnostics
            .sortedWith(compareBy({ severityOrder(it.severity) }, { it.start }))
            .map { d ->
                val pos = session?.document?.offsetToPosition(d.start.coerceIn(0, session.document.length))
                DiagnosticUiModel(
                    offset = d.start,
                    endOffset = d.end,
                    line = pos?.line ?: 0,
                    column = pos?.column ?: 0,
                    severity = d.severity,
                    message = d.message,
                    code = d.code,
                )
            }
        updateState {
            copy(
                activeDiagnostics = ui,
                bottomPanelTabs = bottomPanelTabs.map {
                    if (it.id == "diag") it.copy(count = if (problems == 0) null else problems, error = errors > 0) else it
                },
            )
        }
    }
    private fun severityOrder(s: com.ahmadkharfan.androidstudiolite.feature.editor.engine.DiagnosticSeverity): Int = when (s) {
        com.ahmadkharfan.androidstudiolite.feature.editor.engine.DiagnosticSeverity.Error -> 0
        com.ahmadkharfan.androidstudiolite.feature.editor.engine.DiagnosticSeverity.Warning -> 1
        com.ahmadkharfan.androidstudiolite.feature.editor.engine.DiagnosticSeverity.Info -> 2
        com.ahmadkharfan.androidstudiolite.feature.editor.engine.DiagnosticSeverity.Hint -> 3
    }
    private fun jumpToDiagnostic(diagnostic: DiagnosticUiModel) {
        val id = state.value.activeTabId ?: return
        val session = sessions[id] ?: return
        session.setCaret(diagnostic.offset.coerceIn(0, session.document.length))
        val caret = session.caretPosition
        updateState {
            copy(
                caretLine = caret.line,
                caretColumn = caret.column,
                diagnosticRevealOffset = diagnostic.offset,
                diagnosticRevealNonce = diagnosticRevealNonce + 1,
            )
        }
    }
    fun onCaretMoved(line: Int, column: Int) {
        if (state.value.caretLine == line && state.value.caretColumn == column) return
        updateState { copy(caretLine = line, caretColumn = column) }
    }
    private fun saveActiveTab() {
        val id = state.value.activeTabId ?: return
        val session = sessions[id] ?: return
        val text = session.text
        viewModelScope.launch {
            fileContentRepository.writeText(id, text)
            updateState {
                copy(
                    tabs = tabs.map { if (it.id == id) it.copy(modified = false) else it },
                    snackbarMessage = "Saved ${tabs.firstOrNull { it.id == id }?.name ?: id}",
                )
            }
        }
    }
    private fun updateFindQuery(query: String) {
        val text = state.value.activeTab?.text.orEmpty()
        val count = if (query.isEmpty()) 0 else countOccurrences(text, query)
        updateState {
            copy(
                findQuery = query,
                findMatchCount = count,
                findCurrentMatch = if (count > 0) 1 else 0,
            )
        }
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
        val kept = state.value.tabs.filter { it.id == state.value.activeTabId }
        val keptIds = kept.map { it.id }.toSet()
        sessions.keys.retainAll(keptIds)
        updateState {
            copy(
                tabs = kept,
                memoryPressureActive = false,
                memoryChartExpanded = false,
            )
        }
    }
    private fun simulateLspReindex() {
        if (state.value.lspUpdating) return
        updateState { copy(lspUpdating = true) }
        viewModelScope.launch {
            delay(800)
            updateState { copy(lspUpdating = false) }
        }
    }
    private fun closeTab(id: String) {
        sessions.remove(id)
        val remaining = state.value.tabs.filterNot { it.id == id }
        updateState {
            copy(
                tabs = remaining,
                activeTabId = if (activeTabId == id) remaining.lastOrNull()?.id else activeTabId,
            )
        }
    }
    private fun openFile(id: String, name: String) {
        if (state.value.tabs.any { it.id == id }) {
            updateState { copy(activeTabId = id, openRailTool = null) }
            return
        }
        viewModelScope.launch {
            val content = fileContentRepository.readText(id)
            val language = EditorLanguage.fromFileName(name)
            sessions[id] = EditorSession(content, language, filePath = id)
            val tab = EditorTabUiModel(
                id = id,
                name = name,
                text = content,
                language = language,
                modified = false,
                breadcrumb = breadcrumbFor(id, name),
            )
            if (state.value.tabs.any { it.id == id }) {
                updateState { copy(activeTabId = id, openRailTool = null) }
            } else {
                updateState { copy(tabs = tabs + tab, activeTabId = id, openRailTool = null) }
            }
        }
    }
    private fun breadcrumbFor(id: String, name: String): List<String> {
        val root = projectRootPath
        val relative = if (root != null && id.startsWith(root)) id.removePrefix(root).trimStart('/') else id
        val parts = relative.split('/').filter { it.isNotEmpty() }
        return if (parts.size > 1) parts else listOf(name)
    }
    private fun runProject(simulateFailure: Boolean = false) {
        if (state.value.running) return
        updateState {
            copy(
                running = true,
                bottomPanelExpanded = true,
                activeBottomTabId = "build",
                buildLines = emptyList(),
                buildProgressPercent = 0,
                buildFailed = false,
            )
        }
        viewModelScope.launch {
            val tasks = if (simulateFailure) FAILING_TASKS else SUCCESS_TASKS
            for ((index, task) in tasks.withIndex()) {
                if (task.duration != null) {
                    updateState { copy(buildLines = buildLines + task.copy(status = "running", duration = null)) }
                    delay(300)
                    updateState { copy(buildLines = buildLines.dropLast(1) + task) }
                } else {
                    delay(300)
                    updateState { copy(buildLines = buildLines + task) }
                }
                updateState { copy(buildProgressPercent = ((index + 1) * 100) / tasks.size) }
            }
            updateState {
                copy(
                    running = false,
                    buildFailed = simulateFailure,
                    snackbarMessage = if (simulateFailure) null else "APK installed on device",
                    bottomPanelTabs = bottomPanelTabs.map {
                        if (it.id == "build") it.copy(count = if (simulateFailure) 2 else null, error = simulateFailure) else it
                    },
                )
            }
            if (!simulateFailure) {
                emitAppLogs()
            }
        }
    }
    private suspend fun emitAppLogs() {
        updateState { copy(appLogLines = emptyList()) }
        val timeFormat = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
        for ((level, tag, message) in APP_LOG_TEMPLATE) {
            delay(220)
            val line = AppLogLineUiModel(time = timeFormat.format(java.util.Date()), level = level, tag = tag, message = message)
            updateState { copy(appLogLines = appLogLines + line) }
        }
    }
    private fun FileNode.toUiModel(): EditorFileNodeUiModel = EditorFileNodeUiModel(
        id = id,
        name = name,
        children = children?.map { it.toUiModel() },
        gitStatus = gitStatus,
    )
}
