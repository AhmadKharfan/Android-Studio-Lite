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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

/**
 * How long a run may go with NO [BuildEvent] before it's declared failed. Rolling: each event resets
 * it, so it only trips on genuine silence (dead worker, dropped stream). Generous enough to never
 * trip on a normal build's quiet configuration phase, but bounded so a stuck build can't spin forever.
 */
private const val BUILD_INACTIVITY_TIMEOUT_MS = 120_000L

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

    /**
     * A Main-dispatched scope that outlives [viewModelScope] so a flush triggered by teardown
     * (onCleared) still runs — viewModelScope is already cancelled by then. Reads happen on Main
     * (where edits happen, so [sessions] access is safe); the repository does the actual write off it.
     */
    private val flushScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var launchAfterInstall = true

    /**
     * True for the whole duration of a run — build → download → install — not just the build phase.
     * The console's `isRunning` only covers the build (it flips to Succeeded before install), so it is
     * not a sufficient guard: a second Run pressed while the previous one is still installing would
     * collide on the shared build backend. This flag is the real re-entrancy guard.
     */
    private var runInFlight = false

    /** Rolling inactivity watchdog for the current run — see [launchInactivityWatchdog]. */
    private var buildWatchdogJob: Job? = null

    /** Wall-clock of the current run's most recent [BuildEvent] (or its start); drives the watchdog. */
    @Volatile private var lastBuildEventAt = 0L

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
        val trailDirs = pathToDefaultFile(nodes)?.dropLast(1)?.map { it.id }.orEmpty()
        return (topLevelDirs + trailDirs).toSet()
    }

    /** The file to open when a project first loads, or null for an empty project. */
    private fun firstOpenableFile(nodes: List<FileNode>): FileNode? = pathToDefaultFile(nodes)?.lastOrNull()

    /**
     * Chain of nodes to the file we open by default. Prefer the app's entry point `MainActivity`
     * (what a developer wants to see first on a fresh project), then any source file, then any file.
     */
    private fun pathToDefaultFile(nodes: List<FileNode>): List<FileNode>? =
        pathToFile(nodes) { it.name.equals("MainActivity.kt", true) || it.name.equals("MainActivity.java", true) }
            ?: pathToFile(nodes) { isCodeFile(it.name) }
            ?: pathToFile(nodes) { true }

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
        val previousId = state.value.activeTabId
        viewModelScope.launch {
            // Checkpoint the outgoing tab's edits before switching focus.
            autoSaveJob?.cancel()
            if (previousId != null && !flushDirtyFiles(setOf(previousId))) {
                updateState { copy(snackbarMessage = "Couldn't save file before switching tabs") }
                return@launch
            }
            updateState { copy(activeTabId = id, activeDiagnostics = emptyList()) }
        }
    }
    override fun onCloseTab(id: String) {
        closeTab(id)
    }
    override fun onToggleMenu() {
        updateState { copy(openRailTool = if (openRailTool != null) null else EditorRailTool.Files) }
    }
    override fun onSelectRailTool(tool: EditorRailTool) {
        updateState { copy(openRailTool = if (openRailTool == tool) null else tool) }
    }
    override fun onCloseDrawer() {
        updateState { copy(openRailTool = null) }
    }
    override fun onFocusFileTreeNode(id: String) {
        updateState { copy(selectedFileTreeId = id) }
    }
    override fun onToggleFolder(id: String) {
        updateState {
            copy(
                selectedFileTreeId = id,
                expandedFolderIds = if (id in expandedFolderIds) expandedFolderIds - id else expandedFolderIds + id,
            )
        }
    }
    override fun onOpenFile(id: String, name: String) {
        updateState { copy(selectedFileTreeId = id) }
        openFile(id, name)
    }
    override fun onCreateFileTreeEntry(kind: EditorFileCreateKind, parentPath: String?) {
        val parent = parentPath ?: defaultCreateParentPath() ?: return
        updateState {
            copy(
                fileOperationDialog = EditorFileOperationDialogUiState.Create(
                    parentPath = parent,
                    parentName = File(parent).name.ifBlank { projectName },
                    kind = kind,
                ),
            )
        }
    }
    override fun onFileTreeAction(action: EditorFileTreeAction, id: String, name: String, isDirectory: Boolean) {
        updateState { copy(selectedFileTreeId = id) }
        when (action) {
            EditorFileTreeAction.NewFile -> if (isDirectory) onCreateFileTreeEntry(EditorFileCreateKind.File, id)
            EditorFileTreeAction.NewFolder -> if (isDirectory) onCreateFileTreeEntry(EditorFileCreateKind.Folder, id)
            EditorFileTreeAction.Rename -> updateState {
                copy(fileOperationDialog = EditorFileOperationDialogUiState.Rename(path = id, currentName = name))
            }
            EditorFileTreeAction.Copy -> copyFileTreeEntry(id, name)
            EditorFileTreeAction.Paste -> if (isDirectory) pasteFileTreeEntry(id)
            EditorFileTreeAction.Delete -> updateState {
                copy(fileOperationDialog = EditorFileOperationDialogUiState.Delete(path = id, name = name, isDirectory = isDirectory))
            }
        }
    }
    override fun onConfirmCreateFileTreeEntry(name: String) {
        val dialog = state.value.fileOperationDialog as? EditorFileOperationDialogUiState.Create ?: return
        val trimmed = name.trim()
        if (!isValidFileTreeName(trimmed)) {
            updateState { copy(snackbarMessage = "Use a single non-empty name") }
            return
        }
        viewModelScope.launch {
            runCatching {
                when (dialog.kind) {
                    EditorFileCreateKind.File -> fileTreeRepository.createFile(dialog.parentPath, trimmed)
                    EditorFileCreateKind.Folder -> fileTreeRepository.createDirectory(dialog.parentPath, trimmed)
                }
            }.onSuccess { createdPath ->
                updateState { copy(fileOperationDialog = EditorFileOperationDialogUiState.None) }
                refreshFileTree(expandIds = setOf(dialog.parentPath, createdPath))
                updateState {
                    copy(
                        selectedFileTreeId = createdPath,
                        snackbarMessage = "${if (dialog.kind == EditorFileCreateKind.File) "Created file" else "Created folder"} ${File(createdPath).name}",
                    )
                }
                if (dialog.kind == EditorFileCreateKind.File) openFile(createdPath, File(createdPath).name)
            }.onFailure { error ->
                updateState { copy(snackbarMessage = error.message ?: "Create failed") }
            }
        }
    }
    override fun onConfirmRenameFileTreeEntry(name: String) {
        val dialog = state.value.fileOperationDialog as? EditorFileOperationDialogUiState.Rename ?: return
        val trimmed = name.trim()
        if (!isValidFileTreeName(trimmed)) {
            updateState { copy(snackbarMessage = "Use a single non-empty name") }
            return
        }
        viewModelScope.launch {
            autoSaveJob?.cancel()
            if (!flushDirtyFiles()) {
                updateState { copy(snackbarMessage = "Couldn't save pending editor changes before rename") }
                return@launch
            }
            runCatching { fileTreeRepository.rename(dialog.path, trimmed) }
                .onSuccess { newPath ->
                    remapOpenTabs(dialog.path, newPath)
                    updateState { copy(fileOperationDialog = EditorFileOperationDialogUiState.None) }
                    refreshFileTree(expandIds = setOf(File(newPath).parentFile?.absolutePath.orEmpty()))
                    projectRootPath?.let { syncProjectSymbols(it) }
                    updateState { copy(selectedFileTreeId = newPath, snackbarMessage = "Renamed to ${File(newPath).name}") }
                }
                .onFailure { error -> updateState { copy(snackbarMessage = error.message ?: "Rename failed") } }
        }
    }
    override fun onConfirmDeleteFileTreeEntry() {
        val dialog = state.value.fileOperationDialog as? EditorFileOperationDialogUiState.Delete ?: return
        viewModelScope.launch {
            autoSaveJob?.cancel()
            if (!flushDirtyFiles()) {
                updateState { copy(snackbarMessage = "Couldn't save pending editor changes before delete") }
                return@launch
            }
            runCatching { fileTreeRepository.delete(dialog.path) }
                .onSuccess {
                    closeTabsUnder(dialog.path)
                    updateState { copy(fileOperationDialog = EditorFileOperationDialogUiState.None) }
                    val parent = File(dialog.path).parentFile?.absolutePath
                    refreshFileTree(expandIds = setOf(parent.orEmpty()))
                    projectRootPath?.let { syncProjectSymbols(it) }
                    updateState { copy(selectedFileTreeId = parent, snackbarMessage = "Deleted ${dialog.name}") }
                }
                .onFailure { error -> updateState { copy(snackbarMessage = error.message ?: "Delete failed") } }
        }
    }
    override fun onDismissFileOperationDialog() {
        updateState { copy(fileOperationDialog = EditorFileOperationDialogUiState.None) }
    }
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
        viewModelScope.launch {
            // Persist edits before leaving the editor.
            autoSaveJob?.cancel()
            if (flushDirtyFiles()) {
                emitEffect(EditorEffect.CloseProject)
            } else {
                updateState { copy(snackbarMessage = "Couldn't save pending editor changes") }
            }
        }
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

    private fun defaultCreateParentPath(): String? {
        state.value.selectedFileTreeId?.let { selectedId ->
            findFileTreeNode(state.value.fileTree, selectedId)?.let { node ->
                return if (node.children != null) node.id else File(node.id).parentFile?.absolutePath
            }
        }
        val activeId = state.value.activeTabId
        if (activeId != null) return File(activeId).parentFile?.absolutePath
        return projectRootPath
    }

    private fun findFileTreeNode(nodes: List<EditorFileNodeUiModel>, id: String): EditorFileNodeUiModel? {
        for (node in nodes) {
            if (node.id == id) return node
            node.children?.let { children ->
                findFileTreeNode(children, id)?.let { return it }
            }
        }
        return null
    }

    private fun isValidFileTreeName(name: String): Boolean =
        name.isNotBlank() && !name.contains('/') && !name.contains('\\') && name != "." && name != ".."

    private fun copyFileTreeEntry(id: String, name: String) {
        updateState {
            copy(
                copiedFileTreeEntry = CopiedFileTreeEntryUiModel(path = id, name = name),
                snackbarMessage = "Copied $name",
            )
        }
    }

    private fun pasteFileTreeEntry(parentPath: String) {
        val copied = state.value.copiedFileTreeEntry ?: return
        viewModelScope.launch {
            autoSaveJob?.cancel()
            if (!flushDirtyFiles()) {
                updateState { copy(snackbarMessage = "Couldn't save pending editor changes before paste") }
                return@launch
            }
            runCatching { fileTreeRepository.copy(copied.path, parentPath) }
                .onSuccess { newPath ->
                    refreshFileTree(expandIds = setOf(parentPath))
                    updateState {
                        copy(
                            selectedFileTreeId = newPath,
                            snackbarMessage = "Pasted ${copied.name} as ${File(newPath).name}",
                        )
                    }
                }
                .onFailure { error -> updateState { copy(snackbarMessage = error.message ?: "Paste failed") } }
        }
    }

    private suspend fun refreshFileTree(expandIds: Set<String> = emptySet()) {
        runCatching { fileTreeRepository.getFileTree(projectId) }
            .onSuccess { nodes ->
                updateState {
                    copy(
                        fileTree = nodes.map { it.toUiModel() },
                        expandedFolderIds = expandedFolderIds + expandIds.filter { it.isNotBlank() },
                        isLoadingFileTree = false,
                    )
                }
            }
            .onFailure { error -> updateState { copy(snackbarMessage = error.message ?: "Couldn't refresh project tree") } }
    }

    private fun closeTabsUnder(path: String) {
        val remainingTabs = state.value.tabs.filterNot { isSameOrChild(path, it.id) }
        val remainingIds = remainingTabs.map { it.id }.toSet()
        sessions.keys.retainAll(remainingIds)
        updateState {
            copy(
                tabs = remainingTabs,
                activeTabId = activeTabId?.takeIf { it in remainingIds } ?: remainingTabs.lastOrNull()?.id,
            )
        }
    }

    private fun remapOpenTabs(oldPath: String, newPath: String) {
        val replacements = state.value.tabs
            .filter { isSameOrChild(oldPath, it.id) }
            .associate { tab -> tab.id to renamedPathFor(tab.id, oldPath, newPath) }
        if (replacements.isEmpty()) return
        replacements.forEach { (oldId, newId) ->
            val oldSession = sessions.remove(oldId)
            if (oldSession != null) {
                sessions[newId] = EditorSession(oldSession.text, EditorLanguage.fromFileName(File(newId).name), filePath = newId)
            }
        }
        updateState {
            copy(
                tabs = tabs.map { tab ->
                    val newId = replacements[tab.id]
                    if (newId == null) tab else tab.copy(
                        id = newId,
                        name = File(newId).name,
                        language = EditorLanguage.fromFileName(File(newId).name),
                        breadcrumb = breadcrumbFor(newId, File(newId).name),
                    )
                },
                activeTabId = activeTabId?.let { replacements[it] ?: it },
            )
        }
    }

    private fun isSameOrChild(parent: String, path: String): Boolean =
        path == parent || path.startsWith(parent.trimEnd('/') + "/")

    private fun renamedPathFor(path: String, oldPath: String, newPath: String): String =
        if (path == oldPath) newPath else newPath.trimEnd('/') + path.removePrefix(oldPath).let {
            if (it.startsWith("/")) it else "/$it"
        }

    fun onSessionEdited(id: String) {
        val session = sessions[id] ?: return
        val newText = session.text
        updateState {
            copy(tabs = tabs.map { if (it.id == id) it.copy(text = newText, modified = true) else it })
        }
        scheduleAutoSave()
    }

    /**
     * Debounced auto-save: each edit pushes the write out by [AUTO_SAVE_DEBOUNCE_MS] so we don't hit
     * disk on every keystroke, but a short pause persists the changes with no save tap. When it fires
     * it flushes EVERY dirty tab (not just the last-edited one), so quickly editing file A then file B
     * can't leave A's earlier debounce cancelled and unsaved.
     */
    private fun scheduleAutoSave() {
        if (!autoSaveEnabled) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(AUTO_SAVE_DEBOUNCE_MS)
            flushDirtyFiles()
        }
    }

    /**
     * Writes every tab with unsaved edits to disk now. Idempotent and safe to call repeatedly. A tab's
     * `modified` flag is only cleared if its buffer is unchanged since we snapshotted it, so an edit
     * that lands mid-flush isn't silently marked saved.
     */
    private suspend fun flushDirtyFiles(onlyIds: Set<String>? = null): Boolean {
        val snapshots = state.value.tabs
            .filter { it.modified && (onlyIds == null || it.id in onlyIds) }
            .mapNotNull { tab -> sessions[tab.id]?.let { tab.id to it.text } }
        if (snapshots.isEmpty()) return true
        val writtenIds = mutableSetOf<String>()
        for ((id, text) in snapshots) {
            runCatching { fileContentRepository.writeText(id, text) }
                .onSuccess { writtenIds += id }
        }
        updateState {
            copy(
                tabs = tabs.map { tab ->
                    val written = snapshots.firstOrNull { it.first == tab.id }?.second
                    if (tab.modified && tab.id in writtenIds && written != null && sessions[tab.id]?.text == written) {
                        tab.copy(modified = false)
                    } else {
                        tab
                    }
                },
            )
        }
        return writtenIds.size == snapshots.size
    }

    /**
     * Persists any unsaved edits immediately, cancelling the pending debounce. Called at every point
     * where losing in-memory edits would matter: app backgrounding (ON_STOP), and editor teardown.
     * Non-blocking; runs on [flushScope] so it completes even if [viewModelScope] is torn down next.
     */
    fun flushPendingSaves() {
        autoSaveJob?.cancel()
        flushScope.launch { flushDirtyFiles() }
    }

    override fun onCleared() {
        // Last-chance persist: viewModelScope is cancelled during onCleared, so this must run on the
        // independent flushScope. Left un-cancelled so the in-flight write can complete.
        autoSaveJob?.cancel()
        flushScope.launch { flushDirtyFiles() }
        super.onCleared()
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
        scheduleAutoSave()
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
        viewModelScope.launch {
            autoSaveJob?.cancel()
            val saved = flushDirtyFiles(setOf(id))
            updateState {
                copy(snackbarMessage = if (saved) "Saved ${tabs.firstOrNull { it.id == id }?.name ?: id}" else "Couldn't save file")
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
        viewModelScope.launch {
            autoSaveJob?.cancel()
            if (!flushDirtyFiles()) {
                updateState { copy(snackbarMessage = "Couldn't save pending editor changes") }
                return@launch
            }
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
        viewModelScope.launch {
            autoSaveJob?.cancel()
            if (!flushDirtyFiles(setOf(id))) {
                updateState { copy(snackbarMessage = "Couldn't save file before closing") }
                return@launch
            }
            sessions.remove(id)
            val remaining = state.value.tabs.filterNot { it.id == id }
            updateState {
                copy(
                    tabs = remaining,
                    activeTabId = if (activeTabId == id) remaining.lastOrNull()?.id else activeTabId,
                )
            }
        }
    }
    private fun openFile(id: String, name: String) {
        if (state.value.tabs.any { it.id == id }) {
            val previousId = state.value.activeTabId
            viewModelScope.launch {
                // Checkpoint the current tab's edits before switching to another file.
                autoSaveJob?.cancel()
                if (previousId != null && !flushDirtyFiles(setOf(previousId))) {
                    updateState { copy(snackbarMessage = "Couldn't save file before switching files") }
                    return@launch
                }
                updateState { copy(activeTabId = id, openRailTool = null) }
            }
            return
        }
        viewModelScope.launch {
            // Checkpoint the current tab's edits before opening another file.
            autoSaveJob?.cancel()
            val previousId = state.value.activeTabId
            if (previousId != null && !flushDirtyFiles(setOf(previousId))) {
                updateState { copy(snackbarMessage = "Couldn't save file before opening another file") }
                return@launch
            }
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
        // Guard the WHOLE run (build+install), not just the build phase — see [runInFlight].
        if (runInFlight) return
        val root = projectRootPath?.let { File(it) }
        if (root == null) {
            updateState { copy(snackbarMessage = "Open a project before building") }
            return
        }
        runInFlight = true
        lastBuildEventAt = System.currentTimeMillis()
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
            try {
                // Persist unsaved edits BEFORE packaging — the build zips the project off disk, so an
                // un-flushed buffer would build stale code.
                autoSaveJob?.cancel()
                if (!flushDirtyFiles()) {
                    updateState {
                        copy(
                            running = false,
                            buildFailed = true,
                            buildConsole = buildConsole.copy(status = BuildStatus.Failed, progressMessage = null),
                            snackbarMessage = "Couldn't save pending editor changes before build",
                            bottomPanelTabs = markBuildTab(error = true, count = null),
                        )
                    }
                    return@launch
                }
                val preflight = buildRunCoordinator.preflight(root)
                if (preflight.warnings.isNotEmpty()) applyPreflight(preflight.warnings)
                if (!preflight.canProceed) {
                    val blocker = preflight.warnings.first { it.severity == PreflightSeverity.BLOCKER }
                    updateState {
                        copy(
                            running = false,
                            buildFailed = true,
                            buildConsole = buildConsole.copy(status = BuildStatus.Failed, progressMessage = null),
                            snackbarMessage = blocker.title,
                            bottomPanelTabs = markBuildTab(error = true, count = 1),
                        )
                    }
                    return@launch
                }
                buildRunCoordinator.ensureDebugKeystore()

                // Backstop for a build that goes silent (worker dies before, or during, streaming):
                // without this the console could sit at "Preparing…"/"Starting build…" forever.
                launchInactivityWatchdog()

                val request = BuildRequest(root, APP_MODULE_PATH, variant, kind)
                buildRunCoordinator.build(request).collect { event -> onBuildEvent(event) }
                buildWatchdogJob?.cancel()

                // The flow should have reduced a terminal Finished; if it somehow completed while still
                // Running, don't leave the console spinning — normalise it to a failure.
                if (state.value.buildConsole.isRunning) {
                    updateState {
                        copy(buildConsole = buildConsole.copy(status = BuildStatus.Failed, progressMessage = null))
                    }
                }

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
            } finally {
                buildWatchdogJob?.cancel()
                runInFlight = false
            }
        }
    }

    /**
     * Fails a run that has gone silent. Rolling inactivity timer: every [BuildEvent] pushes the
     * deadline out, so a build that keeps streaming is never touched, but one that stops producing
     * events for [BUILD_INACTIVITY_TIMEOUT_MS] — whether it never started ("Preparing…" forever) or
     * started and then had its worker die mid-flight — is failed and the UI unblocked. This is the
     * app-side guarantee that a run can never hang, independent of the server's own watchdog.
     */
    private fun launchInactivityWatchdog() {
        buildWatchdogJob = viewModelScope.launch {
            while (true) {
                val sinceLastEvent = System.currentTimeMillis() - lastBuildEventAt
                val remaining = BUILD_INACTIVITY_TIMEOUT_MS - sinceLastEvent
                if (remaining > 0) {
                    delay(remaining)
                    continue
                }
                // Silent past the deadline. Only act while the build is still running (during the
                // post-build install phase the console is already terminal — leave it alone).
                if (!state.value.buildConsole.isRunning) return@launch
                updateState {
                    copy(
                        running = false,
                        buildFailed = true,
                        buildConsole = buildConsole.copy(status = BuildStatus.Failed, progressMessage = null),
                        snackbarMessage = "Build stopped responding — tap Run to try again",
                        bottomPanelTabs = markBuildTab(error = true, count = null),
                    )
                }
                buildRunCoordinator.cancel()
                buildJob?.cancel()
                return@launch
            }
        }
    }

    private fun onBuildEvent(event: BuildEvent) {
        lastBuildEventAt = System.currentTimeMillis()
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
