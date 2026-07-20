package com.ahmadkharfan.androidstudiolite.feature.terminal

import com.ahmadkharfan.androidstudiolite.domain.repository.TerminalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class TerminalSessionManager(
    private val repositoryFactory: (hostWorkingDir: String?) -> TerminalRepository,
) {
    private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val sessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    private val _projectActiveIds = MutableStateFlow<Map<String, String>>(emptyMap())
    val projectActiveIds: StateFlow<Map<String, String>> = _projectActiveIds.asStateFlow()

    private var counter = 0

    fun ensureSession(rows: Int, cols: Int) {
        if (_sessions.value.none { it.id.startsWith("term-") }) newSession(rows, cols)
    }

    fun newSession(rows: Int, cols: Int, workingDirectory: String? = null): String {
        counter += 1
        val session = TerminalSession(
            id = "term-$counter",
            title = "Terminal $counter",
            repository = repositoryFactory(workingDirectory),
            workingDirectory = workingDirectory,
            initialRows = rows.coerceAtLeast(1),
            initialCols = cols.coerceAtLeast(1),
        )
        _sessions.value = _sessions.value + session
        _activeId.value = session.id
        return session.id
    }

    fun projectTabs(projectPath: String): List<TerminalSession> {
        val key = projectKey(projectPath)
        return _sessions.value.filter { session ->
            session.id == legacyProjectSessionId(projectPath) ||
                session.id.startsWith("$PROJECT_SESSION_PREFIX$key:")
        }
    }

    fun activeProjectSessionId(projectPath: String): String? {
        val key = projectKey(projectPath)
        _projectActiveIds.value[key]?.let { id ->
            if (_sessions.value.any { it.id == id }) return id
        }
        return projectTabs(projectPath).firstOrNull()?.id
    }

    fun ensureProjectTerminal(projectPath: String, rows: Int, cols: Int): TerminalSession {
        projectTabs(projectPath).firstOrNull()?.let { existing ->
            val id = activeProjectSessionId(projectPath) ?: existing.id
            selectProjectTab(projectPath, id)
            return session(id) ?: existing
        }
        return newProjectTab(projectPath, rows, cols)
    }

    fun newProjectTab(projectPath: String, rows: Int, cols: Int): TerminalSession {
        val key = projectKey(projectPath)
        val index = projectTabs(projectPath).size + 1
        val id = "$PROJECT_SESSION_PREFIX$key:$index"
        val session = TerminalSession(
            id = id,
            title = "Terminal $index",
            repository = repositoryFactory(projectPath),
            workingDirectory = projectPath,
            initialRows = rows.coerceAtLeast(1),
            initialCols = cols.coerceAtLeast(1),
        )
        _sessions.value = _sessions.value + session
        selectProjectTab(projectPath, id)
        return session
    }

    fun selectProjectTab(projectPath: String, sessionId: String) {
        if (_sessions.value.none { it.id == sessionId }) return
        val key = projectKey(projectPath)
        _projectActiveIds.value = _projectActiveIds.value + (key to sessionId)
        _activeId.value = sessionId
    }

    fun closeProjectTab(projectPath: String, sessionId: String) {
        val tabs = projectTabs(projectPath)
        if (tabs.size <= 1) return
        close(sessionId)
        val key = projectKey(projectPath)
        if (_projectActiveIds.value[key] == sessionId) {
            val next = projectTabs(projectPath).firstOrNull()?.id
            _projectActiveIds.value = if (next != null) _projectActiveIds.value + (key to next) else _projectActiveIds.value - key
        }
    }

    fun select(id: String) {
        if (_sessions.value.any { it.id == id }) _activeId.value = id
    }

    fun close(id: String) {
        val current = _sessions.value
        val doomed = current.firstOrNull { it.id == id } ?: return
        val index = current.indexOf(doomed)
        val remaining = current - doomed
        doomed.destroy()
        _sessions.value = remaining
        if (_activeId.value == id) {
            _activeId.value = remaining.getOrNull(index.coerceAtMost(remaining.size - 1))?.id
        }
        _projectActiveIds.value = _projectActiveIds.value.filterValues { activeId -> remaining.any { it.id == activeId } }
    }

    fun session(id: String?): TerminalSession? = _sessions.value.firstOrNull { it.id == id }

    fun activeSession(): TerminalSession? = session(id = _activeId.value)

    fun restartAllSessions(rows: Int, cols: Int) {
        val projectPaths = _sessions.value
            .filter { it.id.startsWith(PROJECT_SESSION_PREFIX) }
            .mapNotNull { it.workingDirectory }
            .distinct()
        val hadGlobalTab = _sessions.value.any { it.id.startsWith("term-") }
        _sessions.value.forEach { it.destroy() }
        _sessions.value = emptyList()
        _activeId.value = null
        _projectActiveIds.value = emptyMap()
        projectPaths.forEach { ensureProjectTerminal(it, rows, cols) }
        when {
            hadGlobalTab -> newSession(rows, cols)
            _sessions.value.isEmpty() -> newSession(rows, cols)
        }
    }

    companion object {
        private const val PROJECT_SESSION_PREFIX = "project:"

        fun projectKey(projectPath: String): String =
            runCatching { File(projectPath).canonicalPath }.getOrDefault(projectPath)

        fun legacyProjectSessionId(projectPath: String): String =
            PROJECT_SESSION_PREFIX + projectKey(projectPath)

        fun projectSessionId(projectPath: String): String = legacyProjectSessionId(projectPath)
    }
}
