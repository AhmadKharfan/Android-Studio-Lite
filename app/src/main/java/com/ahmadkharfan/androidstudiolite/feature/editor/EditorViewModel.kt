package com.ahmadkharfan.androidstudiolite.feature.editor
import androidx.lifecycle.viewModelScope
import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.data.buildsystem.install.InstallEvent
import com.ahmadkharfan.androidstudiolite.data.buildsystem.install.UninstallEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeEvent
import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeType
import com.ahmadkharfan.androidstudiolite.domain.model.FileNode
import com.ahmadkharfan.androidstudiolite.domain.repository.FileContentRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.FileTreeRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.PreferencesRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildConsoleState
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildProblem
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildRunCoordinator
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildStatus
import com.ahmadkharfan.androidstudiolite.feature.buildrun.reduce
import com.ahmadkharfan.androidstudiolite.feature.buildrun.preflight.PreflightSeverity
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorLanguage
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorSession
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
private val BOTTOM_PANEL_TABS = listOf(
    BottomPanelTabUiModel("build", "Build Output", "hammer"),
    BottomPanelTabUiModel("logs", "App Logs", "scroll-text"),
    BottomPanelTabUiModel("term", "Terminal", "terminal"),
    BottomPanelTabUiModel("diag", "Diagnostics", "stethoscope"),
)
private const val AUTO_SAVE_DEBOUNCE_MS = 2000L
private const val APP_MODULE_PATH = ":app"

/** An install attempt held while the user decides whether to uninstall the conflicting package. */
private data class PendingInstall(val apk: File, val applicationId: String)
private val CODE_FILE_EXTENSIONS = setOf("kt", "kts", "java", "xml", "gradle")
class EditorViewModel(
    private val projectId: String,
    private val projectRepository: ProjectRepository,
    private val fileTreeRepository: FileTreeRepository,
    private val fileContentRepository: FileContentRepository,
    private val preferencesRepository: PreferencesRepository,
    private val gradleProjectReader: com.ahmadkharfan.androidstudiolite.data.gradle.GradleProjectReader,
    private val buildRunCoordinator: BuildRunCoordinator,
) : BaseViewModel<EditorUiState, EditorEffect>(
    initialState = EditorUiState(bottomPanelTabs = BOTTOM_PANEL_TABS),
), EditorInteractionListener {
    private val sessions = mutableMapOf<String, EditorSession>()
    private var autoSaveEnabled = true
    private var autoSaveJob: Job? = null
    private var buildJob: Job? = null
    private var launchAfterInstall = true

    /** The install to retry once the user approves clearing a signature conflict. */
    private var pendingInstall: PendingInstall? = null

    /** Absolute path of the open project's root, used to show file-tree paths relative to it. */
    private var projectRootPath: String? = null
    init {
        tryToCollect(
            block = { preferencesRepository.observePreferences() },
            onCollect = { prefs ->
                autoSaveEnabled = prefs.editorAutoSave
                launchAfterInstall = prefs.launchAfterInstall
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
                        projectId = projectId,
                        projectName = projectName,
                        fileTree = nodes.map { it.toUiModel() },
                        expandedFolderIds = defaultExpandedIds(nodes),
                        isLoadingFileTree = false,
                    )
                }
                firstOpenableFile(nodes)?.let { openFile(it.id, it.name) }
                syncProjectSymbols(projectPath)
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

    /**
     * Static "sync": read the Gradle project off disk and index the open module's declarations plus its
     * resolved dependency classes, so completion and diagnostics resolve project symbols instead of only
     * the built-in stdlib catalog. Best-effort — a non-Gradle or unreadable project just leaves the index
     * empty, preserving the previous behaviour.
     */
    private fun syncProjectSymbols(projectPath: String) {
        tryToExecute(
            block = {
                val root = java.io.File(projectPath)
                if (!gradleProjectReader.isGradleProject(root)) {
                    com.ahmadkharfan.androidstudiolite.feature.editor.engine.project.ProjectSymbolIndex.EMPTY
                } else {
                    val model = gradleProjectReader.read(root).model
                    com.ahmadkharfan.androidstudiolite.feature.editor.engine.project.ProjectSymbolIndexer.index(model)
                }
            },
            onSuccess = { index -> updateState { copy(projectIndex = index) } },
        )
    }

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
    override fun onRunProject() = startBuild(variant = "debug", kind = BuildKind.ASSEMBLE, install = true)
    override fun onCancelBuild() = cancelBuild()
    override fun onBuildReleaseApk() = startBuild(variant = "release", kind = BuildKind.ASSEMBLE, install = false)
    override fun onBuildReleaseBundle() = startBuild(variant = "release", kind = BuildKind.BUNDLE, install = false)
    override fun onJumpToBuildProblem(problem: BuildProblem) = jumpToBuildProblem(problem)
    override fun onSimulateBuildFailure() {
        buildRunCoordinator.simulateNextFailure()
        startBuild(variant = "debug", kind = BuildKind.ASSEMBLE, install = true)
    }
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
    /**
     * Drives a real build through the [BuildRunCoordinator] (backed by the flavor's [BuildSystem], or
     * the temporary FakeBuildSystem). Runs the reliability preflight, folds the [BuildEvent] stream
     * into [EditorUiState.buildConsole], and — for a run — installs and launches the produced APK.
     */
    private fun startBuild(variant: String, kind: BuildKind, install: Boolean) {
        if (state.value.buildConsole.isRunning) return
        val root = projectRootPath?.let { File(it) }
        if (root == null) {
            updateState { copy(snackbarMessage = "Open a project before building") }
            return
        }
        updateState {
            copy(
                running = true,
                buildFailed = false,
                bottomPanelExpanded = true,
                activeBottomTabId = "build",
                buildConsole = BuildConsoleState(status = BuildStatus.Running, progressMessage = "Preparing…"),
            )
        }
        buildJob = viewModelScope.launch {
            val preflight = buildRunCoordinator.preflight(root)
            if (preflight.warnings.isNotEmpty()) applyPreflight(preflight.warnings)
            if (!preflight.canProceed) {
                val blocker = preflight.warnings.first { it.severity == PreflightSeverity.BLOCKER }
                updateState {
                    copy(
                        running = false,
                        buildFailed = true,
                        buildConsole = buildConsole.copy(status = BuildStatus.Failed),
                        snackbarMessage = blocker.title,
                        bottomPanelTabs = markBuildTab(error = true, count = 1),
                    )
                }
                return@launch
            }
            buildRunCoordinator.ensureDebugKeystore()

            val request = BuildRequest(root, APP_MODULE_PATH, variant, kind)
            buildRunCoordinator.build(request).collect { event -> onBuildEvent(event) }

            val console = state.value.buildConsole
            val success = console.status == BuildStatus.Succeeded
            buildRunCoordinator.notifyFinished(state.value.projectName, success, console.durationMillis)
            updateState {
                copy(
                    running = false,
                    buildFailed = !success,
                    bottomPanelTabs = markBuildTab(error = !success, count = if (console.errorCount > 0) console.errorCount else null),
                )
            }
            if (success && install) installArtifact(console)
        }
    }

    private fun onBuildEvent(event: BuildEvent) {
        updateState { copy(buildConsole = buildConsole.reduce(event)) }
    }

    /** Surfaces preflight warnings as a leading, non-blocking note in the build console log. */
    private fun applyPreflight(warnings: List<com.ahmadkharfan.androidstudiolite.feature.buildrun.preflight.PreflightWarning>) {
        val prefix = com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildLogLine(
            text = "Preflight: " + warnings.joinToString("; ") { "${it.title} — ${it.detail}" },
            isError = warnings.any { it.severity == PreflightSeverity.BLOCKER },
        )
        updateState { copy(buildConsole = buildConsole.copy(logs = listOf(prefix) + buildConsole.logs)) }
    }

    private suspend fun installArtifact(console: BuildConsoleState) {
        val artifact = console.artifact
        if (artifact == null || artifact.kind != BuildEvent.ArtifactKind.APK) {
            updateState { copy(snackbarMessage = "Build succeeded") }
            return
        }
        val apk = File(artifact.path)
        if (!apk.isFile) {
            // The temporary FakeBuildSystem produces a placeholder path with no bytes on disk.
            updateState { copy(snackbarMessage = "Build succeeded — install skipped (no APK on disk)") }
            return
        }
        val applicationId = projectRootPath
            ?.let { buildRunCoordinator.resolveApplicationId(File(it), APP_MODULE_PATH) }
        runInstall(apk, applicationId)
    }

    /** One install attempt; a signature conflict parks the retry behind a confirmation dialog. */
    private suspend fun runInstall(apk: File, applicationId: String?) {
        buildRunCoordinator.install(apk, applicationId, autoLaunch = launchAfterInstall).collect { event ->
            when (event) {
                is InstallEvent.Conflict -> onInstallConflict(apk, event)
                is InstallEvent.Preparing -> updateState { copy(snackbarMessage = "Installing ${apk.name}…") }
                is InstallEvent.AwaitingConfirmation -> updateState { copy(snackbarMessage = "Confirm the install prompt") }
                is InstallEvent.Installed -> updateState {
                    copy(
                        snackbarMessage = if (event.launched) "Installed and launched"
                        else "Installed ${event.packageName ?: apk.name}",
                    )
                }
                is InstallEvent.Failed -> updateState { copy(snackbarMessage = "Install failed: ${event.reason}") }
            }
        }
    }

    /**
     * The APK is signed differently from the installed copy. Only reachable for an app installed from
     * a build made before the worker got its fixed debug keystore — every build now shares one
     * signature, so rebuilds update in place. Recovering means uninstalling, which drops that app's
     * data, so ask first.
     */
    private fun onInstallConflict(apk: File, event: InstallEvent.Conflict) {
        val applicationId = event.packageName
        if (applicationId == null) {
            // Nothing to uninstall by name — surface it rather than offering a fix we can't perform.
            updateState { copy(snackbarMessage = "Install failed: ${event.reason}") }
            return
        }
        pendingInstall = PendingInstall(apk, applicationId)
        updateState { copy(installConflict = InstallConflictUiModel(applicationId)) }
    }

    override fun onConfirmInstallConflictUninstall() {
        val pending = pendingInstall ?: return
        pendingInstall = null
        updateState { copy(installConflict = null) }
        viewModelScope.launch {
            var uninstalled = false
            buildRunCoordinator.uninstall(pending.applicationId).collect { event ->
                when (event) {
                    is UninstallEvent.Uninstalling ->
                        updateState { copy(snackbarMessage = "Uninstalling ${pending.applicationId}…") }
                    is UninstallEvent.AwaitingConfirmation ->
                        updateState { copy(snackbarMessage = "Confirm the uninstall prompt") }
                    is UninstallEvent.Uninstalled -> uninstalled = true
                    is UninstallEvent.Failed ->
                        updateState { copy(snackbarMessage = "Uninstall failed: ${event.reason}") }
                }
            }
            // Only retry once the package is actually gone; a second conflict would just re-prompt.
            if (uninstalled) runInstall(pending.apk, pending.applicationId)
        }
    }

    override fun onDismissInstallConflict() {
        pendingInstall = null
        updateState {
            copy(
                installConflict = null,
                snackbarMessage = "Install cancelled — the installed app is signed differently",
            )
        }
    }

    private fun cancelBuild() {
        if (!state.value.buildConsole.isRunning) return
        buildRunCoordinator.cancel()
        buildJob?.cancel()
        updateState {
            copy(
                running = false,
                buildConsole = buildConsole.copy(status = BuildStatus.Cancelled, progressMessage = null),
                snackbarMessage = "Build cancelled",
                bottomPanelTabs = markBuildTab(error = false, count = null),
            )
        }
    }

    /** Opens the problem's file (if any) and moves the caret to its line, like a Problems-list jump. */
    private fun jumpToBuildProblem(problem: BuildProblem) {
        val path = problem.filePath ?: return
        val name = problem.fileName ?: path.substringAfterLast('/')
        openFile(path, name)
        val line = problem.line ?: return
        viewModelScope.launch {
            // Give openFile a moment to create the session, then position the caret.
            repeat(20) {
                val session = sessions[path] ?: run { delay(20); return@repeat }
                val offset = session.document.positionToOffset(line - 1, (problem.column ?: 1) - 1)
                session.setCaret(offset.coerceIn(0, session.document.length))
                val caret = session.caretPosition
                updateState {
                    copy(
                        caretLine = caret.line,
                        caretColumn = caret.column,
                        diagnosticRevealOffset = offset,
                        diagnosticRevealNonce = diagnosticRevealNonce + 1,
                    )
                }
                return@launch
            }
        }
    }

    private fun markBuildTab(error: Boolean, count: Int?): List<BottomPanelTabUiModel> =
        state.value.bottomPanelTabs.map {
            if (it.id == "build") it.copy(count = count, error = error) else it
        }
    private fun FileNode.toUiModel(): EditorFileNodeUiModel = EditorFileNodeUiModel(
        id = id,
        name = name,
        children = children?.map { it.toUiModel() },
        gitStatus = gitStatus,
    )
}
