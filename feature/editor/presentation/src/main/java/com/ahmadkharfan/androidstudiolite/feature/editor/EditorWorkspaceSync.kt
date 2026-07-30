package com.ahmadkharfan.androidstudiolite.feature.editor

import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeEvent
import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeType
import com.ahmadkharfan.androidstudiolite.domain.repository.FileContentRepository
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorSession
import com.ahmadkharfan.androidstudiolite.feature.editor.filetree.canonicalPath
import java.io.File

class EditorWorkspaceSync(
    private val sessions: MutableMap<String, EditorSession>,
    private val fileContentRepository: FileContentRepository,
    private val projectRootPath: () -> String?,
    private val state: () -> EditorUiState,
    private val updateState: (EditorUiState.() -> EditorUiState) -> Unit,
    private val latestRootGenerations: () -> Map<String, Long>,
    private val refreshFileTree: suspend (expandIds: Set<String>) -> Unit,
    private val closeTabsUnder: (path: String) -> Unit,
    private val remapOpenTabs: (oldPath: String, newPath: String) -> Unit,
    private val requestGutter: (tabId: String, immediate: Boolean) -> Unit,
) {
    private var lastReconciledGeneration = 0L

    suspend fun onFileEvent(event: FileChangeEvent) {
        when (event) {
            is FileChangeEvent.PathChanged -> onPathChanged(event)
            is FileChangeEvent.RootInvalidated -> reconcileRoot(event.root, event.generation)
        }
    }

    suspend fun reconcileLatestGeneration() {
        val root = projectRootPath()?.let(::canonicalPath) ?: return
        val generation = latestRootGenerations()[root] ?: return
        reconcileRoot(root, generation)
    }

    private suspend fun onPathChanged(event: FileChangeEvent.PathChanged) {
        val projectRoot = projectRootPath()?.let(::canonicalPath) ?: return
        if (!isUnderProject(event.path, projectRoot)) return

        when (event.type) {
            FileChangeType.MODIFIED -> reloadOpenTabFromDisk(event.path)
            FileChangeType.CREATED -> {
                refreshFileTree(setOf(File(event.path).parentFile?.absolutePath.orEmpty()))
                reloadOpenTabFromDisk(event.path)
            }
            FileChangeType.DELETED -> {
                refreshFileTree(emptySet())
                closeTabsUnder(event.path)
            }
            FileChangeType.MOVED -> {
                refreshFileTree(emptySet())
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
        val tab = state().tabs.firstOrNull { canonicalPath(it.id) == canonical } ?: return
        val onDisk = runCatching { fileContentRepository.readText(tab.id) }.getOrNull() ?: return
        val session = sessions[tab.id] ?: return
        if (onDisk == session.text) return
        val hadLocalEdits = tab.modified
        sessions[tab.id] = EditorSession(onDisk, tab.language, filePath = tab.id)
        updateState {
            copy(
                tabs = tabs.map { if (it.id == tab.id) it.copy(text = onDisk, modified = false) else it },
                snackbarMessage = if (hadLocalEdits) {
                    "‘${tab.name}’ was updated outside the editor; local unsaved edits were replaced"
                } else {
                    snackbarMessage
                },
            )
        }
        if (state().activeTabId == tab.id) {
            requestGutter(tab.id, true)
        }
    }

    private fun isUnderProject(path: String, projectRoot: String): Boolean {
        val canonical = canonicalPath(path)
        val root = canonicalPath(projectRoot)
        return canonical == root || canonical.startsWith("$root/")
    }

    private suspend fun reconcileRoot(root: String, generation: Long) {
        val projectRoot = projectRootPath()?.let(::canonicalPath) ?: return
        if (canonicalPath(root) != projectRoot || generation <= lastReconciledGeneration) return
        lastReconciledGeneration = generation

        refreshFileTree(emptySet())
        val snapshot = state().tabs
        val dirtyNames = snapshot.filter { it.modified }.map { it.name }
        val (reloaded, removed) = readCleanTabsFromDisk(snapshot.filterNot { it.modified })
        applyReconciledBuffers(snapshot, reloaded, removed)
        updateState {
            val reconciledTabs = tabs.filterNot { it.id in removed }.map { tab ->
                reloaded[tab.id]?.let { tab.copy(text = it, modified = false) } ?: tab
            }
            copy(
                tabs = reconciledTabs,
                activeTabId = activeTabId?.takeIf { id -> reconciledTabs.any { it.id == id } }
                    ?: reconciledTabs.lastOrNull()?.id,
                snackbarMessage = reconcileMessage(dirtyNames, removed.size, snackbarMessage),
            )
        }
        state().activeTabId?.let { requestGutter(it, true) }
    }

    private fun applyReconciledBuffers(
        snapshot: List<EditorTabUiModel>,
        reloaded: Map<String, String>,
        removed: Set<String>,
    ) {
        reloaded.forEach { (id, text) ->
            val tab = snapshot.first { it.id == id }
            sessions[id] = EditorSession(text, tab.language, filePath = id)
        }
        removed.forEach { sessions.remove(it) }
    }

    private fun reconcileMessage(dirtyNames: List<String>, removedCount: Int, current: String?): String? = when {
        dirtyNames.isNotEmpty() -> "‘${dirtyNames.first()}’ changed on disk; unsaved edits were kept"
        removedCount > 0 -> "Closed $removedCount file(s) removed by Git"
        else -> current
    }

    private suspend fun readCleanTabsFromDisk(
        cleanTabs: List<EditorTabUiModel>,
    ): Pair<Map<String, String>, Set<String>> {
        val reloaded = mutableMapOf<String, String>()
        val removed = mutableSetOf<String>()
        cleanTabs.forEach { tab ->
            if (!File(tab.id).isFile) {
                removed += tab.id
            } else {
                runCatching { fileContentRepository.readText(tab.id) }
                    .onSuccess { reloaded[tab.id] = it }
            }
        }
        return reloaded to removed
    }
}
