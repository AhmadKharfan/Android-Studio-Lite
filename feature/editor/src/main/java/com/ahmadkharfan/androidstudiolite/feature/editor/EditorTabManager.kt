package com.ahmadkharfan.androidstudiolite.feature.editor

import com.ahmadkharfan.androidstudiolite.domain.repository.FileContentRepository
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CodeFormatter
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorLanguage
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorSession
import com.ahmadkharfan.androidstudiolite.feature.editor.filetree.breadcrumbFor
import com.ahmadkharfan.androidstudiolite.feature.editor.filetree.isSameOrChild
import com.ahmadkharfan.androidstudiolite.feature.editor.filetree.renamedPathFor
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val AUTO_SAVE_DEBOUNCE_MS = 2000L

class EditorTabManager(
    private val scope: CoroutineScope,
    private val flushScope: CoroutineScope,
    private val fileContentRepository: FileContentRepository,
    private val state: () -> EditorUiState,
    private val updateState: (EditorUiState.() -> EditorUiState) -> Unit,
    private val projectRootPath: () -> String?,
    private val isAutoSaveEnabled: () -> Boolean,
    private val requestGutter: (tabId: String, immediate: Boolean) -> Unit,
    private val showSnackbar: (message: String) -> Unit,
) {
    val sessions: MutableMap<String, EditorSession> = mutableMapOf()
    private var autoSaveJob: Job? = null

    fun sessionFor(id: String?): EditorSession? = id?.let { sessions[it] }

    fun cancelAutoSave() {
        autoSaveJob?.cancel()
    }

    fun selectTab(id: String) {
        scope.launch {
            if (!flushActiveTab("Couldn't save file before switching tabs")) return@launch
            updateState { copy(activeTabId = id) }
            requestGutter(id, true)
        }
    }

    private suspend fun flushActiveTab(failureMessage: String): Boolean {
        cancelAutoSave()
        val previousId = state().activeTabId ?: return true
        if (flushDirtyFiles(setOf(previousId))) return true
        showSnackbar(failureMessage)
        return false
    }

    fun closeTab(id: String) {
        scope.launch {
            cancelAutoSave()
            if (!flushDirtyFiles(setOf(id))) {
                showSnackbar("Couldn't save file before closing")
                return@launch
            }
            sessions.remove(id)
            val remaining = state().tabs.filterNot { it.id == id }
            updateState {
                copy(
                    tabs = remaining,
                    activeTabId = if (activeTabId == id) remaining.lastOrNull()?.id else activeTabId,
                )
            }
        }
    }

    fun openFile(id: String, name: String) {
        if (state().tabs.any { it.id == id }) {
            activateExistingTab(id)
        } else {
            openNewTab(id, name)
        }
    }

    private fun activateExistingTab(id: String) {
        scope.launch {
            if (!flushActiveTab("Couldn't save file before switching files")) return@launch
            updateState { copy(activeTabId = id, openRailTool = null) }
            requestGutter(id, true)
        }
    }

    private fun openNewTab(id: String, name: String) {
        scope.launch {
            if (!flushActiveTab("Couldn't save file before opening another file")) return@launch
            val content = fileContentRepository.readText(id)
            val language = EditorLanguage.fromFileName(name)
            sessions[id] = EditorSession(content, language, filePath = id)
            val tab = newTab(id, name, content, language)
            if (state().tabs.any { it.id == id }) {
                updateState { copy(activeTabId = id, openRailTool = null) }
            } else {
                updateState { copy(tabs = tabs + tab, activeTabId = id, openRailTool = null) }
            }
            requestGutter(id, true)
        }
    }

    private fun newTab(id: String, name: String, content: String, language: EditorLanguage) = EditorTabUiModel(
        id = id,
        name = name,
        text = content,
        language = language,
        modified = false,
        breadcrumb = breadcrumbFor(projectRootPath(), id, name),
    )

    fun onSessionEdited(id: String) {
        val session = sessions[id] ?: return
        val newText = session.text
        updateState {
            copy(tabs = tabs.map { if (it.id == id) it.copy(text = newText, modified = true) else it })
        }
        scheduleAutoSave()
        if (id == state().activeTabId) requestGutter(id, false)
    }

    fun undo() = applyToActiveSession { it.undo() }

    fun redo() = applyToActiveSession { it.redo() }

    private fun applyToActiveSession(action: (EditorSession) -> Boolean) {
        val id = state().activeTabId ?: return
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

    fun reformatActiveTab(tabSize: Int) {
        val id = state().activeTabId ?: return
        val session = sessions[id] ?: return
        val original = session.text
        val formatted = CodeFormatter.reformat(original, tabSize, session.language)
        if (formatted == original) {
            showSnackbar("Already formatted")
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

    fun onCaretMoved(line: Int, column: Int) {
        if (state().caretLine == line && state().caretColumn == column) return
        updateState { copy(caretLine = line, caretColumn = column) }
    }

    fun saveActiveTab() {
        val id = state().activeTabId ?: return
        scope.launch {
            cancelAutoSave()
            val saved = flushDirtyFiles(setOf(id))
            val name = state().tabs.firstOrNull { it.id == id }?.name ?: id
            showSnackbar(if (saved) "Saved $name" else "Couldn't save file")
        }
    }

    fun closeTabsUnder(path: String) {
        val remainingTabs = state().tabs.filterNot { isSameOrChild(path, it.id) }
        val remainingIds = remainingTabs.map { it.id }.toSet()
        sessions.keys.retainAll(remainingIds)
        updateState {
            copy(
                tabs = remainingTabs,
                activeTabId = activeTabId?.takeIf { it in remainingIds } ?: remainingTabs.lastOrNull()?.id,
            )
        }
    }

    fun remapOpenTabs(oldPath: String, newPath: String) {
        val replacements = state().tabs
            .filter { isSameOrChild(oldPath, it.id) }
            .associate { tab -> tab.id to renamedPathFor(tab.id, oldPath, newPath) }
        if (replacements.isEmpty()) return
        replacements.forEach { (oldId, newId) -> remapSession(oldId, newId) }
        updateState {
            copy(
                tabs = tabs.map { tab -> replacements[tab.id]?.let { renamedTab(tab, it) } ?: tab },
                activeTabId = activeTabId?.let { replacements[it] ?: it },
            )
        }
    }

    private fun remapSession(oldId: String, newId: String) {
        val oldSession = sessions.remove(oldId) ?: return
        sessions[newId] = EditorSession(oldSession.text, EditorLanguage.fromFileName(File(newId).name), filePath = newId)
    }

    private fun renamedTab(tab: EditorTabUiModel, newId: String) = tab.copy(
        id = newId,
        name = File(newId).name,
        language = EditorLanguage.fromFileName(File(newId).name),
        breadcrumb = breadcrumbFor(projectRootPath(), newId, File(newId).name),
    )

    private fun scheduleAutoSave() {
        if (!isAutoSaveEnabled()) return
        autoSaveJob?.cancel()
        autoSaveJob = scope.launch {
            delay(AUTO_SAVE_DEBOUNCE_MS)
            flushDirtyFiles()
        }
    }

    suspend fun flushDirtyFiles(onlyIds: Set<String>? = null): Boolean {
        val savedText = state().tabs
            .filter { it.modified && (onlyIds == null || it.id in onlyIds) }
            .mapNotNull { tab -> sessions[tab.id]?.let { tab.id to it.text } }
            .toMap()
        if (savedText.isEmpty()) return true
        val writtenIds = savedText.mapNotNull { (id, text) ->
            id.takeIf { runCatching { fileContentRepository.writeText(id, text) }.isSuccess }
        }.toSet()
        updateState { clearSavedTabs(savedText, writtenIds) }
        return writtenIds.size == savedText.size
    }

    private fun EditorUiState.clearSavedTabs(savedText: Map<String, String>, writtenIds: Set<String>): EditorUiState =
        copy(
            tabs = tabs.map { tab ->
                val text = savedText[tab.id]
                if (tab.modified && tab.id in writtenIds && text != null && sessions[tab.id]?.text == text) {
                    tab.copy(modified = false)
                } else {
                    tab
                }
            },
        )

    fun flushPendingSaves() {
        cancelAutoSave()
        flushScope.launch { flushDirtyFiles() }
    }

    suspend fun flushDirtyBuffers(): Boolean = withContext(Dispatchers.Main.immediate) {
        cancelAutoSave()
        flushDirtyFiles()
    }

    fun shutdown() {
        cancelAutoSave()
        flushScope.launch { flushDirtyFiles() }
    }
}
