package com.ahmadkharfan.androidstudiolite.feature.editor
import androidx.lifecycle.viewModelScope
import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.core.network.NetworkMonitor
import com.ahmadkharfan.androidstudiolite.domain.model.FileNode
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileState
import com.ahmadkharfan.androidstudiolite.domain.repository.FileContentRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.FileTreeRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.PreferencesRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.GitRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.WorkspaceWriteGate
import com.ahmadkharfan.androidstudiolite.domain.repository.WorkspaceWriteHandler
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildProblem
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildRunApi
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildStatus
import com.ahmadkharfan.androidstudiolite.feature.buildrun.InstallExecutionState
import com.ahmadkharfan.androidstudiolite.feature.buildrun.RunTargetResolver
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.project.ProjectSymbolIndex
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.project.ProjectSymbolIndexer
import com.ahmadkharfan.androidstudiolite.feature.editor.filetree.ancestorFolderIds
import com.ahmadkharfan.androidstudiolite.feature.editor.filetree.canonicalPath
import com.ahmadkharfan.androidstudiolite.feature.editor.filetree.defaultExpandedIds
import com.ahmadkharfan.androidstudiolite.feature.editor.filetree.findFileTreeNode
import com.ahmadkharfan.androidstudiolite.feature.editor.filetree.firstOpenableFile
import com.ahmadkharfan.androidstudiolite.feature.editor.filetree.isValidFileTreeName
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorSession
import java.io.File
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
private val BOTTOM_PANEL_TABS = listOf(
    BottomPanelTabUiModel("build", "Build Output", "hammer"),
    BottomPanelTabUiModel("term", "Terminal", "terminal"),
)
private const val DEFAULT_BOTTOM_PANEL_HEIGHT_DP = 260f

internal fun expandedBottomPanelHeight(current: Float): Float =
    if (current > 0f) current else DEFAULT_BOTTOM_PANEL_HEIGHT_DP

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
    private var isAutoSaveEnabled = true
    private val flushScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var shouldLaunchAfterInstall = true

    private var projectRootPath: String? = null
    private var latestGitFiles: Map<String, GitFileState> = emptyMap()
    private var workspaceWriteRegistration: Closeable? = null
    private var latestRootGenerations: Map<String, Long> = emptyMap()
    private val findController = EditorFindController()

    private val tabManager = EditorTabManager(
        scope = viewModelScope,
        flushScope = flushScope,
        fileContentRepository = fileContentRepository,
        state = { state.value },
        updateState = { reducer -> updateState(reducer) },
        projectRootPath = { projectRootPath },
        isAutoSaveEnabled = { isAutoSaveEnabled },
        requestGutter = { tabId, immediate -> requestGutter(tabId, immediate) },
        showSnackbar = { message -> showSnackbar(message) },
    )

    private val gutterController = EditorGitGutterController(
        scope = viewModelScope,
        gitRepository = { gitRepository },
        projectRootPath = { projectRootPath },
        latestGitFiles = { latestGitFiles },
        activeTabId = { state.value.activeTabId },
        bufferText = { tabId -> tabManager.sessionFor(tabId)?.text },
        isBufferUnchanged = { tabId, buffer -> tabManager.sessionFor(tabId)?.text == buffer },
        applyMarkers = { tabId, markers ->
            updateState { copy(tabs = tabs.map { if (it.id == tabId) it.copy(gitLineStatus = markers) else it }) }
        },
        clearMarkers = { tabId ->
            updateState { copy(tabs = tabs.map { if (it.id == tabId) it.copy(gitLineStatus = emptyMap()) else it }) }
        },
    )

    private val buildController = EditorBuildController(
        projectId = projectId,
        scope = viewModelScope,
        buildRunCoordinator = buildRunCoordinator,
        gradleProjectReader = gradleProjectReader,
        networkMonitor = networkMonitor,
        projectRootPath = { projectRootPath },
        state = { state.value },
        updateState = { reducer -> updateState(reducer) },
        emitEffect = { effect -> emitEffect(effect) },
        shouldLaunchAfterInstall = { shouldLaunchAfterInstall },
        cancelAutoSave = { tabManager.cancelAutoSave() },
        flushDirtyFiles = { tabManager.flushDirtyFiles() },
    )

    private val workspaceSync = EditorWorkspaceSync(
        sessions = tabManager.sessions,
        fileContentRepository = fileContentRepository,
        projectRootPath = { projectRootPath },
        state = { state.value },
        updateState = { reducer -> updateState(reducer) },
        latestRootGenerations = { latestRootGenerations },
        refreshFileTree = { expandIds -> refreshFileTree(expandIds) },
        closeTabsUnder = { path -> tabManager.closeTabsUnder(path) },
        remapOpenTabs = { oldPath, newPath -> tabManager.remapOpenTabs(oldPath, newPath) },
        requestGutter = { tabId, immediate -> requestGutter(tabId, immediate) },
    )

    init {
        gutterController.start()
        observeEditorPreferences()
        observeBuildExecution()
        loadProjectAndOpenDefaultFile()
        observeExternalFileChanges()
    }

    private fun observeEditorPreferences() = tryToCollect(
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

    private fun observeBuildExecution() = tryToCollect(
        block = { buildRunCoordinator.execution },
        onCollect = { execution ->
            if (execution.projectId != projectId) return@tryToCollect
            val console = execution.console
            updateState {
                copy(
                    running = execution.isActive,
                    buildFailed = console.status == BuildStatus.Failed ||
                        execution.installState == InstallExecutionState.Failed,
                    buildConsole = console,
                    installConflict = execution.installConflictPackage?.let(::InstallConflictUiModel),
                    bottomPanelTabs = markBuildTab(
                        bottomPanelTabs,
                        error = console.status == BuildStatus.Failed,
                        count = console.errorCount.takeIf { it > 0 },
                    ),
                    snackbarMessage = installSnackbar(execution.installState, execution.installFailureReason, snackbarMessage),
                )
            }
        },
        dispatcher = Dispatchers.Main.immediate,
    )

    private fun installSnackbar(installState: InstallExecutionState, failureReason: String?, current: String?): String? =
        when (installState) {
            InstallExecutionState.Preparing -> "Installing…"
            InstallExecutionState.AwaitingConfirmation -> "Waiting for install confirmation. Check notifications if needed."
            InstallExecutionState.Installed -> "Installed successfully"
            InstallExecutionState.Failed -> failureReason ?: "Installation failed"
            InstallExecutionState.None -> current
        }

    private fun loadProjectAndOpenDefaultFile() = tryToExecute(
        block = {
            val project = projectRepository.openProject(projectId)
            val nodes = fileTreeRepository.getFileTree(project.path)
            Triple(project.name, project.path, nodes)
        },
        onSuccess = { (projectName, projectPath, nodes) ->
            projectRootPath = projectPath
            buildController.cacheProjectModel(null)
            registerWorkspaceWriteGuard(projectPath)
            updateState {
                copy(
                    projectId = this@EditorViewModel.projectId,
                    projectName = projectName,
                    projectRootPath = projectPath,
                    fileTree = nodes.map { it.toEditorNode(projectRootPath, latestGitFiles) },
                    expandedFolderIds = defaultExpandedIds(nodes),
                    isLoadingFileTree = false,
                )
            }
            startProjectSession(projectPath, nodes)
        },
    )

    private fun startProjectSession(projectPath: String, nodes: List<FileNode>) {
        firstOpenableFile(nodes)?.let { tabManager.openFile(it.id, it.name) }
        observeGitState(File(projectPath))
        syncProjectSymbols(projectPath)
        viewModelScope.launch { workspaceSync.reconcileLatestGeneration() }
        viewModelScope.launch { buildRunCoordinator.recover(projectId) }
    }

    private fun registerWorkspaceWriteGuard(projectPath: String) {
        workspaceWriteRegistration?.close()
        workspaceWriteRegistration = workspaceWriteGate?.register(
            File(projectPath),
            WorkspaceWriteHandler {
                check(tabManager.flushDirtyBuffers()) { "Couldn't save pending editor changes" }
            },
        )
    }

    private fun observeExternalFileChanges() {
        tryToCollect(
            block = { fileContentRepository.observeChanges() },
            onCollect = { event -> workspaceSync.onFileEvent(event) },
            dispatcher = Dispatchers.Main.immediate,
        )
        tryToCollect(
            block = { fileContentRepository.rootInvalidationGenerations() },
            onCollect = { generations ->
                latestRootGenerations = generations
                workspaceSync.reconcileLatestGeneration()
            },
            dispatcher = Dispatchers.Main.immediate,
        )
    }

    private fun syncProjectSymbols(projectPath: String) {
        tryToExecute(
            block = { readProjectSymbols(File(projectPath)) },
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

    private suspend fun readProjectSymbols(root: File): SyncSymbolsResult {
        if (!gradleProjectReader.isGradleProject(root)) {
            return SyncSymbolsResult(
                index = ProjectSymbolIndex.EMPTY,
                runModulePath = "",
                availableVariants = emptyList(),
                selectedVariant = "",
            )
        }
        val model = gradleProjectReader.read(root).model
        buildController.cacheProjectModel(root.absolutePath to model)
        val app = RunTargetResolver.resolveAppModule(model)
        val remembered = preferencesRepository.getSelectedVariant(projectId)?.takeIf { it.isNotBlank() }
            ?: state.value.selectedVariant
        return SyncSymbolsResult(
            index = ProjectSymbolIndexer.index(model),
            runModulePath = app?.path.orEmpty(),
            availableVariants = RunTargetResolver.availableVariantNames(app),
            selectedVariant = RunTargetResolver.resolveSelectedVariant(app, remembered),
        )
    }

    private data class SyncSymbolsResult(
        val index: ProjectSymbolIndex,
        val runModulePath: String,
        val availableVariants: List<String>,
        val selectedVariant: String,
    )

    fun sessionFor(id: String?): EditorSession? = tabManager.sessionFor(id)

    private fun showSnackbar(message: String) = updateState { copy(snackbarMessage = message) }

    private suspend fun saveBeforeFileMutation(failureMessage: String): Boolean {
        if (tabManager.flushDirtyBuffers()) return true
        showSnackbar(failureMessage)
        return false
    }

    override fun onSelectTab(id: String) = tabManager.selectTab(id)
    override fun onCloseTab(id: String) = tabManager.closeTab(id)
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
        tabManager.openFile(id, name)
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
            EditorFileTreeAction.AddToGitignore -> addToGitignore(id)
        }
    }

    private fun addToGitignore(path: String) {
        val root = projectRootPath?.let(::File) ?: return
        val git = gitRepository ?: return
        tryToExecute(
            block = { git.addToGitignore(root, path) },
            onSuccess = { showSnackbar("Added to .gitignore") },
            onError = { showSnackbar(it.message ?: "Could not update .gitignore") },
        )
    }
    override fun onConfirmCreateFileTreeEntry(name: String) {
        val dialog = state.value.fileOperationDialog as? EditorFileOperationDialogUiState.Create ?: return
        val trimmed = name.trim()
        if (!isValidFileTreeName(trimmed)) {
            showSnackbar("Use a single non-empty name")
            return
        }
        viewModelScope.launch {
            runCatching {
                when (dialog.kind) {
                    EditorFileCreateKind.File -> fileTreeRepository.createFile(dialog.parentPath, trimmed)
                    EditorFileCreateKind.Folder -> fileTreeRepository.createDirectory(dialog.parentPath, trimmed)
                }
            }.onSuccess { createdPath -> onFileTreeEntryCreated(dialog, createdPath) }
                .onFailure { error -> showSnackbar(error.message ?: "Create failed") }
        }
    }

    private suspend fun onFileTreeEntryCreated(dialog: EditorFileOperationDialogUiState.Create, createdPath: String) {
        val isFile = dialog.kind == EditorFileCreateKind.File
        updateState { copy(fileOperationDialog = EditorFileOperationDialogUiState.None) }
        refreshFileTree(expandIds = setOf(dialog.parentPath, createdPath))
        updateState {
            copy(
                selectedFileTreeId = createdPath,
                snackbarMessage = "${if (isFile) "Created file" else "Created folder"} ${File(createdPath).name}",
            )
        }
        if (isFile) tabManager.openFile(createdPath, File(createdPath).name)
    }
    override fun onConfirmRenameFileTreeEntry(name: String) {
        val dialog = state.value.fileOperationDialog as? EditorFileOperationDialogUiState.Rename ?: return
        val trimmed = name.trim()
        if (!isValidFileTreeName(trimmed)) {
            showSnackbar("Use a single non-empty name")
            return
        }
        viewModelScope.launch {
            if (!saveBeforeFileMutation("Couldn't save pending editor changes before rename")) return@launch
            runCatching { fileTreeRepository.rename(dialog.path, trimmed) }
                .onSuccess { newPath ->
                    tabManager.remapOpenTabs(dialog.path, newPath)
                    updateState { copy(fileOperationDialog = EditorFileOperationDialogUiState.None) }
                    refreshFileTree(expandIds = setOf(File(newPath).parentFile?.absolutePath.orEmpty()))
                    projectRootPath?.let { syncProjectSymbols(it) }
                    updateState { copy(selectedFileTreeId = newPath, snackbarMessage = "Renamed to ${File(newPath).name}") }
                }
                .onFailure { error -> showSnackbar(error.message ?: "Rename failed") }
        }
    }
    override fun onConfirmDeleteFileTreeEntry() {
        val dialog = state.value.fileOperationDialog as? EditorFileOperationDialogUiState.Delete ?: return
        viewModelScope.launch {
            if (!saveBeforeFileMutation("Couldn't save pending editor changes before delete")) return@launch
            runCatching { fileTreeRepository.delete(dialog.path) }
                .onSuccess {
                    tabManager.closeTabsUnder(dialog.path)
                    updateState { copy(fileOperationDialog = EditorFileOperationDialogUiState.None) }
                    val parent = File(dialog.path).parentFile?.absolutePath
                    refreshFileTree(expandIds = setOf(parent.orEmpty()))
                    projectRootPath?.let { syncProjectSymbols(it) }
                    updateState { copy(selectedFileTreeId = parent, snackbarMessage = "Deleted ${dialog.name}") }
                }
                .onFailure { error -> showSnackbar(error.message ?: "Delete failed") }
        }
    }
    override fun onDismissFileOperationDialog() {
        updateState { copy(fileOperationDialog = EditorFileOperationDialogUiState.None) }
    }
    override fun onRunProject() = buildController.run()
    override fun onSelectVariant(variant: String) {
        updateState { copy(selectedVariant = variant) }
        viewModelScope.launch { runCatching { preferencesRepository.setSelectedVariant(projectId, variant) } }
    }
    override fun onCancelBuild() = buildController.cancel()
    override fun onBuildRelease() = buildController.buildRelease()
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
    override fun onSave() = tabManager.saveActiveTab()
    override fun onUndo() = tabManager.undo()
    override fun onRedo() = tabManager.redo()
    override fun onReformatCode() = tabManager.reformatActiveTab(state.value.editorTabSize)
    override fun onCloseProject() {
        viewModelScope.launch {
            if (tabManager.flushDirtyBuffers()) {
                emitEffect(EditorEffect.CloseProject)
            } else {
                showSnackbar("Couldn't save pending editor changes")
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
    override fun onToggleMarkdownPreview() {
        updateState { copy(markdownPreview = !markdownPreview) }
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
            if (!saveBeforeFileMutation("Couldn't save pending editor changes before paste")) return@launch
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
                .onFailure { error -> showSnackbar(error.message ?: "Paste failed") }
        }
    }

    private suspend fun refreshFileTree(expandIds: Set<String> = emptySet()) {
        runCatching { fileTreeRepository.getFileTree(projectRootPath ?: projectId) }
            .onSuccess { nodes ->
                updateState {
                    copy(
                        fileTree = nodes.map { it.toEditorNode(projectRootPath, latestGitFiles) },
                        expandedFolderIds = expandedFolderIds + expandIds.filter { it.isNotBlank() },
                        isLoadingFileTree = false,
                    )
                }
            }
            .onFailure { error -> showSnackbar(error.message ?: "Couldn't refresh project tree") }
    }

    fun onSessionEdited(id: String) = tabManager.onSessionEdited(id)

    fun flushPendingSaves() = tabManager.flushPendingSaves()

    suspend fun flushDirtyBuffers(): Boolean = tabManager.flushDirtyBuffers()

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
                        fileTree = fileTree.map { it.withGitStatus(root, latestGitFiles) },
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
        tabManager.shutdown()
        workspaceWriteRegistration?.close()
        super.onCleared()
    }
    fun onCaretMoved(line: Int, column: Int) = tabManager.onCaretMoved(line, column)
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
    override fun onConfirmInstallConflictUninstall() = buildController.confirmInstallConflictUninstall()

    override fun onDismissInstallConflict() = buildController.dismissInstallConflict()

    private fun jumpToBuildProblem(problem: BuildProblem) {
        val path = problem.filePath ?: return
        val name = problem.fileName ?: path.substringAfterLast('/')
        tabManager.openFile(path, name)
        val line = problem.line ?: return
        viewModelScope.launch {

            repeat(20) {
                val session = tabManager.sessionFor(path) ?: run { delay(20); return@repeat }
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

}
