package com.ahmadkharfan.androidstudiolite.feature.editor
import androidx.lifecycle.viewModelScope
import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.core.network.NetworkMonitor
import com.ahmadkharfan.androidstudiolite.data.buildsystem.install.InstallEvent
import com.ahmadkharfan.androidstudiolite.data.buildsystem.install.UninstallEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeEvent
import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeType
import com.ahmadkharfan.androidstudiolite.domain.model.FileNode
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileState
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileStatus
import com.ahmadkharfan.androidstudiolite.domain.repository.FileContentRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.FileTreeRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.PreferencesRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.GitRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.WorkspaceWriteGate
import com.ahmadkharfan.androidstudiolite.domain.repository.WorkspaceWriteHandler
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildConsoleState
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildClientMeta
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildProblem
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildRunApi
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildStatus
import com.ahmadkharfan.androidstudiolite.feature.buildrun.reduce
import com.ahmadkharfan.androidstudiolite.feature.buildrun.preflight.PreflightSeverity
import kotlinx.coroutines.flow.Flow
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CodeFormatter
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorLanguage
import com.ahmadkharfan.androidstudiolite.feature.editor.filetree.ancestorFolderIds
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorSession
import java.io.File
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
private val BOTTOM_PANEL_TABS = listOf(
    BottomPanelTabUiModel("build", "Build Output", "hammer"),
    BottomPanelTabUiModel("term", "Terminal", "terminal"),
)
private const val AUTO_SAVE_DEBOUNCE_MS = 2000L
private const val DEFAULT_BOTTOM_PANEL_HEIGHT_DP = 260f

private fun expandedBottomPanelHeight(current: Float): Float =
    if (current > 0f) current else DEFAULT_BOTTOM_PANEL_HEIGHT_DP

private const val BUILD_INACTIVITY_TIMEOUT_MS = 900_000L

private data class PendingInstall(val apk: File, val applicationId: String)
private val CODE_FILE_EXTENSIONS = setOf("kt", "kts", "java", "xml", "gradle")
class EditorViewModel(
    private val projectId: String,
    private val projectRepository: ProjectRepository,
    private val fileTreeRepository: FileTreeRepository,
    private val fileContentRepository: FileContentRepository,
    private val preferencesRepository: PreferencesRepository,
    private val gradleProjectReader: com.ahmadkharfan.androidstudiolite.data.gradle.GradleProjectReader,
    private val buildRunCoordinator: BuildRunApi,
    private val networkMonitor: NetworkMonitor? = null,
    private val gitRepository: GitRepository? = null,
    private val workspaceWriteGate: WorkspaceWriteGate? = null,
) : BaseViewModel<EditorUiState, EditorEffect>(
    initialState = EditorUiState(bottomPanelTabs = BOTTOM_PANEL_TABS),
), EditorInteractionListener {
    private val sessions = mutableMapOf<String, EditorSession>()
    private var isAutoSaveEnabled = true
    private var autoSaveJob: Job? = null
    private var buildJob: Job? = null

    private val flushScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var shouldLaunchAfterInstall = true

    private var isBuildInFlight = false

    private var buildWatchdogJob: Job? = null

    @Volatile private var lastBuildEventAt = 0L

    private var pendingInstall: PendingInstall? = null

    private var projectRootPath: String? = null
    private var latestGitFiles: Map<String, GitFileState> = emptyMap()
    private var workspaceWriteRegistration: Closeable? = null
    private var latestRootGenerations: Map<String, Long> = emptyMap()
    private var lastReconciledGeneration = 0L
    private val findController = EditorFindController()
    private val gutterController = EditorGitGutterController(
        scope = viewModelScope,
        gitRepository = { gitRepository },
        projectRootPath = { projectRootPath },
        latestGitFiles = { latestGitFiles },
        activeTabId = { state.value.activeTabId },
        bufferText = { tabId -> sessions[tabId]?.text },
        isBufferUnchanged = { tabId, buffer -> sessions[tabId]?.text == buffer },
        applyMarkers = { tabId, markers ->
            updateState { copy(tabs = tabs.map { if (it.id == tabId) it.copy(gitLineStatus = markers) else it }) }
        },
        clearMarkers = { tabId ->
            updateState { copy(tabs = tabs.map { if (it.id == tabId) it.copy(gitLineStatus = emptyMap()) else it }) }
        },
    )
    init {
        gutterController.start()
        tryToCollect(
            block = { preferencesRepository.observePreferences() },
            onCollect = { prefs ->
                isAutoSaveEnabled = prefs.editorAutoSave
                shouldLaunchAfterInstall = prefs.launchAfterInstall
                updateState {
                    copy(
                        editorFontSize = prefs.editorFontSize,
                        editorTabSize = prefs.editorTabSize,
                        editorThemeId = prefs.editorThemeId,
                        editorFontFamily = prefs.editorFontFamily,
                        buildOutputAab = prefs.buildOutputAab,
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
                workspaceWriteRegistration?.close()
                workspaceWriteRegistration = workspaceWriteGate?.register(
                    File(projectPath),
                    WorkspaceWriteHandler {
                        check(flushDirtyBuffers()) { "Couldn't save pending editor changes" }
                    },
                )
                updateState {
                    copy(


                        projectId = this@EditorViewModel.projectId,
                        projectName = projectName,
                        projectRootPath = projectPath,
                        fileTree = nodes.map { it.toUiModel() },
                        expandedFolderIds = defaultExpandedIds(nodes),
                        isLoadingFileTree = false,
                    )
                }
                firstOpenableFile(nodes)?.let { openFile(it.id, it.name) }
                observeGitState(File(projectPath))
                syncProjectSymbols(projectPath)
                viewModelScope.launch { reconcileLatestRootGeneration() }
                resumeActiveBuildIfNeeded(projectPath, projectName)
            },
        )

        tryToCollect(
            block = { fileContentRepository.observeChanges() },
            onCollect = { event -> onExternalFileEvent(event) },
            dispatcher = Dispatchers.Main.immediate,
        )
        tryToCollect(
            block = { fileContentRepository.rootInvalidationGenerations() },
            onCollect = { generations ->
                latestRootGenerations = generations
                reconcileLatestRootGeneration()
            },
            dispatcher = Dispatchers.Main.immediate,
        )
    }

    private fun defaultExpandedIds(nodes: List<FileNode>): Set<String> {
        val topLevelDirs = nodes.filter { it.children != null }.map { it.id }
        val trailDirs = pathToDefaultFile(nodes)?.dropLast(1)?.map { it.id }.orEmpty()
        return (topLevelDirs + trailDirs).toSet()
    }

    private fun firstOpenableFile(nodes: List<FileNode>): FileNode? = pathToDefaultFile(nodes)?.lastOrNull()

    private fun pathToDefaultFile(nodes: List<FileNode>): List<FileNode>? =
        pathToFile(nodes) { it.name.equals("MainActivity.kt", true) || it.name.equals("MainActivity.java", true) }
            ?: pathToFile(nodes) { isCodeFile(it.name) }
            ?: pathToFile(nodes) { true }

    private fun pathToFile(
        nodes: List<FileNode>,
        trail: List<FileNode> = emptyList(),
        predicate: (FileNode) -> Boolean,
    ): List<FileNode>? {
        for (node in nodes) {
            val nextTrail = trail + node
            val children = node.children
            if (children == null) {
                if (predicate(node)) return nextTrail
            } else {
                pathToFile(children, nextTrail, predicate)?.let { return it }
            }
        }
        return null
    }

    private fun isCodeFile(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in CODE_FILE_EXTENSIONS

    private fun syncProjectSymbols(projectPath: String) {
        tryToExecute(
            block = {
                val root = java.io.File(projectPath)
                if (!gradleProjectReader.isGradleProject(root)) {
                    SyncSymbolsResult(
                        index = com.ahmadkharfan.androidstudiolite.feature.editor.engine.project.ProjectSymbolIndex.EMPTY,
                        runModulePath = ":app",
                        availableVariants = listOf("debug", "release"),
                        selectedVariant = state.value.selectedVariant,
                    )
                } else {
                    val model = gradleProjectReader.read(root).model
                    val app = com.ahmadkharfan.androidstudiolite.feature.buildrun.RunTargetResolver
                        .resolveAppModule(model)
                    val variants = com.ahmadkharfan.androidstudiolite.feature.buildrun.RunTargetResolver
                        .availableVariantNames(app)


                    val selected = com.ahmadkharfan.androidstudiolite.feature.buildrun.RunTargetResolver
                        .resolveSelectedVariant(app, state.value.selectedVariant)
                    SyncSymbolsResult(
                        index = com.ahmadkharfan.androidstudiolite.feature.editor.engine.project.ProjectSymbolIndexer.index(model),
                        runModulePath = app?.path ?: ":app",
                        availableVariants = variants,
                        selectedVariant = selected,
                    )
                }
            },
            onSuccess = { result ->
                updateState {
                    copy(
                        projectIndex = result.index,
                        runModulePath = result.runModulePath,
                        availableVariants = result.availableVariants,
                        selectedVariant = result.selectedVariant,
                    )
                }
            },
        )
    }

    private data class SyncSymbolsResult(
        val index: com.ahmadkharfan.androidstudiolite.feature.editor.engine.project.ProjectSymbolIndex,
        val runModulePath: String,
        val availableVariants: List<String>,
        val selectedVariant: String,
    )

    private suspend fun onExternalFileEvent(event: FileChangeEvent) {
        when (event) {
            is FileChangeEvent.PathChanged -> onExternalPathChanged(event)
            is FileChangeEvent.RootInvalidated -> reconcileRoot(event.root, event.generation)
        }
    }

    private suspend fun onExternalPathChanged(event: FileChangeEvent.PathChanged) {
        val projectRoot = projectRootPath?.let(::canonicalPath) ?: return
        if (!isUnderProject(event.path, projectRoot)) return

        when (event.type) {
            FileChangeType.MODIFIED -> reloadOpenTabFromDisk(event.path)
            FileChangeType.CREATED -> {
                refreshFileTree(expandIds = setOf(File(event.path).parentFile?.absolutePath.orEmpty()))
                reloadOpenTabFromDisk(event.path)
            }
            FileChangeType.DELETED -> {
                refreshFileTree()
                closeTabsUnder(event.path)
            }
            FileChangeType.MOVED -> {
                refreshFileTree()
                val oldPath = event.oldPath ?: return
                if (isUnderProject(oldPath, projectRoot)) {
                    remapOpenTabs(oldPath, event.path)
                }
                reloadOpenTabFromDisk(event.path)
            }
        }
    }

    private suspend fun reloadOpenTabFromDisk(path: String) {
        val canonical = canonicalPath(path)
        val tab = state.value.tabs.firstOrNull { canonicalPath(it.id) == canonical } ?: return
        val onDisk = runCatching { fileContentRepository.readText(tab.id) }.getOrNull() ?: return
        val session = sessions[tab.id] ?: return
        if (onDisk == session.text) return
        val hadLocalEdits = tab.modified
        sessions[tab.id] = EditorSession(onDisk, tab.language, filePath = tab.id)
        updateState {
            copy(
                tabs = tabs.map { if (it.id == tab.id) it.copy(text = onDisk, modified = false) else it },
                snackbarMessage = when {
                    hadLocalEdits -> "‘${tab.name}’ was updated outside the editor; local unsaved edits were replaced"
                    else -> snackbarMessage
                },
            )
        }
        if (state.value.activeTabId == tab.id) {
            requestGutter(tab.id, immediate = true)
        }
    }

    private fun isUnderProject(path: String, projectRoot: String): Boolean {
        val canonical = canonicalPath(path)
        val root = canonicalPath(projectRoot)
        return canonical == root || canonical.startsWith("$root/")
    }

    private suspend fun reconcileLatestRootGeneration() {
        val root = projectRootPath?.let(::canonicalPath) ?: return
        val generation = latestRootGenerations[root] ?: return
        reconcileRoot(root, generation)
    }

    private suspend fun reconcileRoot(root: String, generation: Long) {
        val projectRoot = projectRootPath?.let(::canonicalPath) ?: return
        if (canonicalPath(root) != projectRoot || generation <= lastReconciledGeneration) return
        lastReconciledGeneration = generation

        refreshFileTree()
        val snapshot = state.value.tabs
        val dirtyNames = snapshot.filter { it.modified }.map { it.name }
        val cleanTabs = snapshot.filterNot { it.modified }
        val reloaded = mutableMapOf<String, String>()
        val removed = mutableSetOf<String>()
        cleanTabs.forEach { tab ->
            val file = File(tab.id)
            if (!file.isFile) {
                removed += tab.id
            } else {
                runCatching { fileContentRepository.readText(tab.id) }
                    .onSuccess { reloaded[tab.id] = it }
            }
        }
        reloaded.forEach { (id, text) ->
            val tab = snapshot.first { it.id == id }
            sessions[id] = EditorSession(text, tab.language, filePath = id)
        }
        removed.forEach { sessions.remove(it) }
        updateState {
            val reconciledTabs = tabs.filterNot { it.id in removed }.map { tab ->
                reloaded[tab.id]?.let { tab.copy(text = it, modified = false) } ?: tab
            }
            copy(
                tabs = reconciledTabs,
                activeTabId = activeTabId?.takeIf { id -> reconciledTabs.any { it.id == id } }
                    ?: reconciledTabs.lastOrNull()?.id,
                snackbarMessage = when {
                    dirtyNames.isNotEmpty() -> "‘${dirtyNames.first()}’ changed on disk; unsaved edits were kept"
                    removed.isNotEmpty() -> "Closed ${removed.size} file(s) removed by Git"
                    else -> snackbarMessage
                },
            )
        }
        state.value.activeTabId?.let { requestGutter(it, immediate = true) }
    }

    private fun canonicalPath(path: String): String = runCatching { File(path).canonicalPath }
        .getOrDefault(File(path).absolutePath)
    fun sessionFor(id: String?): EditorSession? = id?.let { sessions[it] }

    override fun onSelectTab(id: String) {
        val previousId = state.value.activeTabId
        viewModelScope.launch {

            autoSaveJob?.cancel()
            if (previousId != null && !flushDirtyFiles(setOf(previousId))) {
                updateState { copy(snackbarMessage = "Couldn't save file before switching tabs") }
                return@launch
            }
            updateState { copy(activeTabId = id) }
            requestGutter(id, immediate = true)
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

    override fun onRevealFileTreeNode(id: String) {
        val ancestors = ancestorFolderIds(state.value.fileTree, id)
        updateState {
            copy(
                selectedFileTreeId = id,
                expandedFolderIds = expandedFolderIds + ancestors,
            )
        }
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
            EditorFileTreeAction.ShowHistory, EditorFileTreeAction.Blame -> Unit
            EditorFileTreeAction.AddToGitignore -> {
                val root = projectRootPath?.let(::File) ?: return
                val git = gitRepository ?: return
                tryToExecute(
                    block = { git.addToGitignore(root, id) },
                    onSuccess = { updateState { copy(snackbarMessage = "Added to .gitignore") } },
                    onError = { updateState { copy(snackbarMessage = it.message ?: "Could not update .gitignore") } },
                )
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
    override fun onRunProject() = startBuild(variant = state.value.selectedVariant, kind = BuildKind.ASSEMBLE, install = true)
    override fun onSelectVariant(variant: String) = updateState { copy(selectedVariant = variant) }
    override fun onCancelBuild() = cancelBuild()
    override fun onBuildRelease() {
        val kind = if (state.value.buildOutputAab) BuildKind.BUNDLE else BuildKind.ASSEMBLE


        startBuild(variant = "release", kind = kind, install = false)
    }
    override fun onJumpToBuildProblem(problem: BuildProblem) = jumpToBuildProblem(problem)
    override fun onSelectBottomTab(id: String) {
        updateState {
            copy(
                activeBottomTabId = id,
                bottomPanelHeightDp = expandedBottomPanelHeight(bottomPanelHeightDp),
            )
        }
    }
    override fun onToggleBottomPanel() {
        updateState {
            copy(
                bottomPanelHeightDp = if (bottomPanelHeightDp > 0f) 0f else DEFAULT_BOTTOM_PANEL_HEIGHT_DP,
            )
        }
    }
    override fun onBottomPanelHeightChanged(heightDp: Float) {
        updateState { copy(bottomPanelHeightDp = heightDp.coerceAtLeast(0f)) }
    }
    override fun onSave() = saveActiveTab()
    override fun onUndo() = undoRedoActive { it.undo() }
    override fun onRedo() = undoRedoActive { it.redo() }
    override fun onReformatCode() {
        val id = state.value.activeTabId ?: return
        val session = sessions[id] ?: return
        val original = session.text
        val formatted = CodeFormatter.reformat(original, state.value.editorTabSize, session.language)
        if (formatted == original) {
            updateState { copy(snackbarMessage = "Already formatted") }
            return
        }
        session.replaceRange(0, original.length, formatted)
        val caret = session.caretPosition
        updateState {
            copy(
                tabs = tabs.map { if (it.id == id) it.copy(text = session.text, modified = true) else it },
                caretLine = caret.line,
                caretColumn = caret.column,
                snackbarMessage = "Reformatted",
            )
        }
        scheduleAutoSave()
    }
    override fun onCloseProject() {
        viewModelScope.launch {
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
    override fun onToggleFindBar() {
        val snapshot = findController.toggledBar(state.value.findBarOpen)
        updateState {
            copy(
                findBarOpen = snapshot.findBarOpen,
                findQuery = snapshot.findQuery,
                findMatchCount = snapshot.findMatchCount,
                findCurrentMatch = snapshot.findCurrentMatch,
            )
        }
    }
    override fun onFindQueryChanged(query: String) = updateFindQuery(query)
    override fun onFindNext() {
        updateState {
            copy(findCurrentMatch = findController.nextMatch(findMatchCount, findCurrentMatch))
        }
    }
    override fun onFindPrevious() {
        updateState {
            copy(findCurrentMatch = findController.previousMatch(findMatchCount, findCurrentMatch))
        }
    }
    override fun onToggleAutocompleteDemo() {
        updateState { copy(autocompletePopupVisible = !autocompletePopupVisible) }
    }

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
        if (id == state.value.activeTabId) requestGutter(id, immediate = false)
    }

    private fun scheduleAutoSave() {
        if (!isAutoSaveEnabled) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(AUTO_SAVE_DEBOUNCE_MS)
            flushDirtyFiles()
        }
    }

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

    fun flushPendingSaves() {
        autoSaveJob?.cancel()
        flushScope.launch { flushDirtyFiles() }
    }

    suspend fun flushDirtyBuffers(): Boolean = withContext(Dispatchers.Main.immediate) {
        autoSaveJob?.cancel()
        flushDirtyFiles()
    }

    fun onAppForegrounded() {
        val root = projectRootPath?.let(::File) ?: return
        val git = gitRepository ?: return
        tryToExecute(block = { git.onAppForegrounded(root) })
    }

    private fun observeGitState(root: File) {
        val git = gitRepository ?: return
        tryToCollect(
            block = { git.observeState(root) },
            onCollect = { gitState ->
                latestGitFiles = gitState.files.associateBy { it.path }
                val gitUi = gitState.toEditorGitUiModel()
                updateState {
                    copy(
                        gitStatusText = gitUi.statusText,
                        gitPendingChangeCount = gitUi.pendingChangeCount,
                        fileTree = fileTree.map { it.withGitStatus(root) },
                    )
                }
                state.value.activeTabId?.let { requestGutter(it, immediate = true) }
            },
        )
        tryToExecute(block = { git.refresh(root) })
    }

    private fun requestGutter(tabId: String, immediate: Boolean) {
        gutterController.request(tabId, immediate)
    }

    private fun clearGutter(tabId: String) {
        gutterController.clear(tabId)
    }

    override fun onCleared() {


        autoSaveJob?.cancel()
        flushScope.launch { flushDirtyFiles() }
        workspaceWriteRegistration?.close()
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
        val snapshot = findController.queryChanged(state.value.activeTab?.text.orEmpty(), query)
        updateState {
            copy(
                findQuery = snapshot.findQuery,
                findMatchCount = snapshot.findMatchCount,
                findCurrentMatch = snapshot.findCurrentMatch,
            )
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
            activateExistingTab(id)
            return
        }
        openNewTab(id, name)
    }

    private fun activateExistingTab(id: String) {
        val previousId = state.value.activeTabId
        viewModelScope.launch {
            autoSaveJob?.cancel()
            if (previousId != null && !flushDirtyFiles(setOf(previousId))) {
                updateState { copy(snackbarMessage = "Couldn't save file before switching files") }
                return@launch
            }
            updateState { copy(activeTabId = id, openRailTool = null) }
            requestGutter(id, immediate = true)
        }
    }

    private fun openNewTab(id: String, name: String) {
        viewModelScope.launch {
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
            requestGutter(id, immediate = true)
        }
    }

    private fun breadcrumbFor(id: String, name: String): List<String> {
        val root = projectRootPath
        val relative = if (root != null && id.startsWith(root)) id.removePrefix(root).trimStart('/') else id
        val parts = relative.split('/').filter { it.isNotEmpty() }
        return if (parts.size > 1) parts else listOf(name)
    }
    private fun startBuild(variant: String, kind: BuildKind, install: Boolean) {
        if (isBuildInFlight) return
        val root = projectRootPath?.let { File(it) }
        if (root == null) {
            updateState { copy(snackbarMessage = "Open a project before building") }
            return
        }
        if (networkMonitor?.isOnline() == false) {
            showOfflineBuildFailure()
            return
        }
        if (!buildRunCoordinator.canPostNotifications()) {
            emitEffect(EditorEffect.RequestNotificationsPermission)
        }
        isBuildInFlight = true
        lastBuildEventAt = System.currentTimeMillis()
        showBuildRunning()
        buildJob = viewModelScope.launch {
            try {
                if (!prepareWorkspaceForBuild(root)) return@launch
                val targets = resolveBuildTargets(root, variant, install) ?: return@launch
                launchInactivityWatchdog()
                val request = BuildRequest(root, targets.modulePath, targets.variant, kind)
                val meta = BuildClientMeta(
                    projectId = projectId,
                    projectName = state.value.projectName,
                    installAfterSuccess = install,
                )
                collectBuildFlow(buildRunCoordinator.build(request, meta), install = install)
            } finally {
                buildWatchdogJob?.cancel()
                isBuildInFlight = false
            }
        }
    }

    private fun showOfflineBuildFailure() {
        val offlineMsg = "You're offline — connect to the internet and try again."
        updateState {
            copy(
                running = false,
                buildFailed = true,
                bottomPanelHeightDp = expandedBottomPanelHeight(bottomPanelHeightDp),
                activeBottomTabId = "build",
                buildConsole = BuildConsoleState(
                    status = BuildStatus.Failed,
                    problems = listOf(
                        BuildProblem(
                            severity = BuildEvent.ProblemSeverity.ERROR,
                            message = offlineMsg,
                        ),
                    ),
                ),
                snackbarMessage = offlineMsg,
                bottomPanelTabs = markBuildTab(error = true, count = 1),
            )
        }
    }

    private fun showBuildRunning() {
        updateState {
            copy(
                running = true,
                buildFailed = false,
                bottomPanelHeightDp = expandedBottomPanelHeight(bottomPanelHeightDp),
                activeBottomTabId = "build",
                buildConsole = BuildConsoleState(status = BuildStatus.Running, progressMessage = "Preparing…"),
            )
        }
    }

    private suspend fun prepareWorkspaceForBuild(root: File): Boolean {
        autoSaveJob?.cancel()
        if (!flushDirtyFiles()) {
            failBuildPreparation("Couldn't save pending editor changes before build")
            return false
        }
        val preflight = buildRunCoordinator.preflight(root)
        if (preflight.warnings.isNotEmpty()) applyPreflight(preflight.warnings)
        if (!preflight.canProceed) {
            val blocker = preflight.warnings.first { it.severity == PreflightSeverity.BLOCKER }
            failBuildPreparation(blocker.title, problemCount = 1)
            return false
        }
        buildRunCoordinator.ensureDebugKeystore()
        return true
    }

    private fun failBuildPreparation(message: String, problemCount: Int? = null) {
        updateState {
            copy(
                running = false,
                buildFailed = true,
                buildConsole = buildConsole.copy(status = BuildStatus.Failed, progressMessage = null),
                snackbarMessage = message,
                bottomPanelTabs = markBuildTab(error = true, count = problemCount),
            )
        }
    }

    private data class BuildTargets(val modulePath: String, val variant: String)

    private fun resolveBuildTargets(root: File, variant: String, install: Boolean): BuildTargets? {
        val projectModel = runCatching { gradleProjectReader.read(root).model }.getOrNull()
        val appModule = projectModel?.let {
            com.ahmadkharfan.androidstudiolite.feature.buildrun.RunTargetResolver.resolveAppModule(it)
        }
        val modulePath = appModule?.path
            ?: state.value.runModulePath.takeIf { it.isNotBlank() }
            ?: ":app"
        val resolvedVariant = when {
            !install && variant.equals("release", ignoreCase = true) ->
                com.ahmadkharfan.androidstudiolite.feature.buildrun.RunTargetResolver
                    .resolveReleaseVariant(appModule, state.value.selectedVariant)
            else ->
                com.ahmadkharfan.androidstudiolite.feature.buildrun.RunTargetResolver
                    .resolveVariant(appModule, variant)
        }
        val available = com.ahmadkharfan.androidstudiolite.feature.buildrun.RunTargetResolver
            .availableVariantNames(appModule)
        updateState {
            copy(
                selectedVariant = if (install) resolvedVariant else selectedVariant,
                runModulePath = modulePath,
                availableVariants = available,
            )
        }
        return BuildTargets(modulePath, resolvedVariant)
    }

    private fun resumeActiveBuildIfNeeded(projectPath: String, projectName: String) {
        if (isBuildInFlight) return
        viewModelScope.launch {
            val active = buildRunCoordinator.activeBuildFor(projectId) ?: return@launch
            if (isBuildInFlight) return@launch
            if (networkMonitor?.isOnline() == false) return@launch
            val flow = buildRunCoordinator.attachIfActive(
                projectId = projectId,
                projectRoot = File(projectPath),
                projectName = projectName,
            ) ?: return@launch

            isBuildInFlight = true
            lastBuildEventAt = System.currentTimeMillis()
            updateState {
                copy(
                    running = true,
                    buildFailed = false,
                    bottomPanelHeightDp = expandedBottomPanelHeight(bottomPanelHeightDp),
                    activeBottomTabId = "build",
                    buildConsole = BuildConsoleState(
                        status = BuildStatus.Running,
                        progressMessage = "Checking build status…",
                    ),
                )
            }
            buildJob = viewModelScope.launch {
                try {
                    launchInactivityWatchdog()
                    collectBuildFlow(flow, install = active.installAfterSuccess)
                } finally {
                    buildWatchdogJob?.cancel()
                    isBuildInFlight = false
                }
            }
        }
    }

    private suspend fun collectBuildFlow(flow: Flow<BuildEvent>, install: Boolean) {
        try {
            flow.collect { event -> onBuildEvent(event) }
            buildWatchdogJob?.cancel()
            markBuildFailedIfStillRunning()
            val console = state.value.buildConsole
            val success = console.status == BuildStatus.Succeeded
            finishBuildUi(console, success, install)
            if (success && install) {
                buildRunCoordinator.updateKeepAliveProgress(
                    projectId,
                    state.value.projectName,
                    "Installing…",
                )
                installArtifact(console)
            } else {
                buildRunCoordinator.notifyFinished(
                    projectName = state.value.projectName,
                    success = success,
                    durationMillis = console.durationMillis,
                    projectId = projectId,
                    installFollows = false,
                )
            }
        } finally {
            buildWatchdogJob?.cancel()
            buildRunCoordinator.endKeepAlive()
        }
    }

    private fun markBuildFailedIfStillRunning() {
        if (!state.value.buildConsole.isRunning) return
        updateState {
            copy(buildConsole = buildConsole.copy(status = BuildStatus.Failed, progressMessage = null))
        }
    }

    private fun finishBuildUi(console: BuildConsoleState, success: Boolean, install: Boolean) {
        val failureMessage = console.problems
            .lastOrNull { it.severity == BuildEvent.ProblemSeverity.ERROR }
            ?.message
        updateState {
            copy(
                running = false,
                buildFailed = !success,
                snackbarMessage = when {
                    success && install -> "Build succeeded — installing…"
                    success -> snackbarMessage
                    !failureMessage.isNullOrBlank() -> failureMessage
                    else -> "Build failed — open Build Output for details"
                },
                bottomPanelTabs = markBuildTab(
                    error = !success,
                    count = if (console.errorCount > 0) console.errorCount else null,
                ),
            )
        }
    }

    private fun launchInactivityWatchdog() {
        buildWatchdogJob = viewModelScope.launch {
            while (true) {
                val sinceLastEvent = System.currentTimeMillis() - lastBuildEventAt
                val remaining = BUILD_INACTIVITY_TIMEOUT_MS - sinceLastEvent
                if (remaining > 0) {
                    delay(remaining)
                    continue
                }
                if (!state.value.buildConsole.isRunning) return@launch
                failBuildOnInactivity(offline = networkMonitor?.isOnline() == false)
                return@launch
            }
        }
    }

    private fun failBuildOnInactivity(offline: Boolean) {
        val timeoutMsg = if (offline) {
            "You're offline — connect to the internet and try again."
        } else {
            "Build stopped responding — no progress for several minutes. Tap Run to try again."
        }
        updateState {
            copy(
                running = false,
                buildFailed = true,
                buildConsole = buildConsole.copy(
                    status = BuildStatus.Failed,
                    progressMessage = null,
                    problems = buildConsole.problems + BuildProblem(
                        severity = BuildEvent.ProblemSeverity.ERROR,
                        message = timeoutMsg,
                    ),
                ),
                snackbarMessage = timeoutMsg,
                bottomPanelTabs = markBuildTab(error = true, count = null),
            )
        }
        buildRunCoordinator.cancel()
        buildJob?.cancel()
    }

    private fun onBuildEvent(event: BuildEvent) {
        lastBuildEventAt = System.currentTimeMillis()
        updateState { copy(buildConsole = buildConsole.reduce(event)) }
    }

    private fun applyPreflight(warnings: List<com.ahmadkharfan.androidstudiolite.feature.buildrun.preflight.PreflightWarning>) {
        val prefix = com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildLogLine(
            text = "Preflight: " + warnings.joinToString("; ") { "${it.title} — ${it.detail}" },
            isError = warnings.any { it.severity == PreflightSeverity.BLOCKER },
        )
        updateState { copy(buildConsole = buildConsole.copy(logs = listOf(prefix) + buildConsole.logs)) }
    }

    private suspend fun installArtifact(console: BuildConsoleState) {
        val apk = resolveInstallableApk(console) ?: return
        val applicationId = projectRootPath
            ?.let { buildRunCoordinator.resolveApplicationId(File(it), state.value.runModulePath) }
        runInstall(apk, applicationId)
    }

    private fun resolveInstallableApk(console: BuildConsoleState): File? {
        val artifact = console.artifact
        if (artifact == null) {
            updateState {
                copy(snackbarMessage = "Build succeeded but no APK was downloaded — open Build Output for details")
            }
            return null
        }
        if (artifact.kind != BuildEvent.ArtifactKind.APK) {
            updateState {
                copy(snackbarMessage = "Build succeeded — ${artifact.kind.name} can't be installed on-device")
            }
            return null
        }
        if (artifact.name.contains("unsigned", ignoreCase = true)) {
            updateState {
                copy(
                    snackbarMessage = "Build produced an unsigned release APK — use a debug variant or add a release keystore",
                )
            }
            return null
        }
        val apk = File(artifact.path)
        if (!apk.isFile) {
            updateState {
                copy(snackbarMessage = "Build succeeded — APK download incomplete, tap Run to try again")
            }
            return null
        }
        return apk
    }

    private suspend fun runInstall(apk: File, applicationId: String?) {
        buildRunCoordinator.install(apk, applicationId, autoLaunch = shouldLaunchAfterInstall).collect { event ->
            when (event) {
                is InstallEvent.Conflict -> onInstallConflict(apk, event)
                is InstallEvent.Preparing -> updateState { copy(snackbarMessage = "Installing ${apk.name}…") }
                is InstallEvent.AwaitingConfirmation -> updateState {
                    copy(snackbarMessage = "Waiting for install confirmation — check notifications if the prompt isn't visible")
                }
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

    private fun onInstallConflict(apk: File, event: InstallEvent.Conflict) {
        val applicationId = event.packageName
        if (applicationId == null) {

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

    private fun jumpToBuildProblem(problem: BuildProblem) {
        val path = problem.filePath ?: return
        val name = problem.fileName ?: path.substringAfterLast('/')
        openFile(path, name)
        val line = problem.line ?: return
        viewModelScope.launch {

            repeat(20) {
                val session = sessions[path] ?: run { delay(20); return@repeat }
                val offset = session.document.positionToOffset(line - 1, (problem.column ?: 1) - 1)
                session.setCaret(offset.coerceIn(0, session.document.length))
                val caret = session.caretPosition
                updateState {
                    copy(
                        caretLine = caret.line,
                        caretColumn = caret.column,
                        editorRevealOffset = offset,
                        editorRevealNonce = editorRevealNonce + 1,
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
        gitStatus = layeredGitStatus(id) ?: gitStatus,
    )

    private fun EditorFileNodeUiModel.withGitStatus(root: File): EditorFileNodeUiModel = copy(
        children = children?.map { it.withGitStatus(root) },
        gitStatus = if (children != null) null else layeredGitStatus(id, root),
    )

    private fun layeredGitStatus(path: String, root: File? = projectRootPath?.let(::File)): GitFileStatus? {
        root ?: return null
        val file = runCatching { File(path).canonicalFile }.getOrDefault(File(path).absoluteFile)
        val canonicalRoot = runCatching { root.canonicalFile }.getOrDefault(root.absoluteFile)
        val relative = runCatching { file.relativeTo(canonicalRoot).invariantSeparatorsPath }.getOrNull()
            ?: return null
        val state = latestGitFiles[relative] ?: return null
        return state.toEditorFileStatus()
    }
}
