package com.ahmadkharfan.androidstudiolite.feature.editor

import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslLineGit
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffKind
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileDiff
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileState
import com.ahmadkharfan.androidstudiolite.domain.repository.GitRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val GUTTER_DEBOUNCE_MS = 300L

class EditorGitGutterController(
    private val scope: CoroutineScope,
    private val gitRepository: () -> GitRepository?,
    private val projectRootPath: () -> String?,
    private val latestGitFiles: () -> Map<String, GitFileState>,
    private val activeTabId: () -> String?,
    private val bufferText: (tabId: String) -> String?,
    private val isBufferUnchanged: (tabId: String, buffer: String) -> Boolean,
    private val applyMarkers: (tabId: String, markers: Map<Int, AslLineGit>) -> Unit,
    private val clearMarkers: (tabId: String) -> Unit,
) {
    private data class Request(val tabId: String, val immediate: Boolean)

    private val requests = MutableSharedFlow<Request>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun start() {
        scope.launch {
            requests.collectLatest { request ->
                if (!request.immediate) delay(GUTTER_DEBOUNCE_MS)
                recompute(request.tabId)
            }
        }
    }

    fun request(tabId: String, immediate: Boolean) {
        requests.tryEmit(Request(tabId, immediate))
    }

    fun clear(tabId: String) = clearMarkers(tabId)

    private suspend fun recompute(tabId: String) {
        if (activeTabId() != tabId) return
        val relative = relativePath(tabId) ?: return clear(tabId)
        val rootPath = projectRootPath() ?: return clear(tabId)
        val root = File(rootPath).canonicalFile
        val git = gitRepository() ?: return clear(tabId)
        if (latestGitFiles()[relative]?.conflictStage != null) return clear(tabId)
        val buffer = bufferText(tabId) ?: return clear(tabId)
        val diff = runCatching { git.diffIndexToBuffer(root, relative, buffer) }.getOrNull()
            ?: return clear(tabId)
        if (diff.isBinary || diff.tooLarge) return clear(tabId)
        val markers = markersFromDiff(diff)
        if (activeTabId() == tabId && isBufferUnchanged(tabId, buffer)) {
            applyMarkers(tabId, markers)
        }
    }

    private fun relativePath(tabId: String): String? {
        val rootPath = projectRootPath() ?: return null
        if (gitRepository() == null) return null
        val root = File(rootPath).canonicalFile
        val file = File(tabId).canonicalFile
        val prefix = root.path.trimEnd(File.separatorChar) + File.separator
        if (!file.path.startsWith(prefix)) return null
        return file.relativeTo(root).invariantSeparatorsPath
    }

    companion object {
        fun markersFromDiff(diff: GitFileDiff): Map<Int, AslLineGit> = buildMap {
            diff.hunks.forEach { hunk ->
                var removed = 0
                val added = mutableListOf<Int>()
                var newCursor = (hunk.newStart - 1).coerceAtLeast(0)
                fun flushEdit() {
                    if (added.isNotEmpty()) {
                        added.forEach { put(it, if (removed > 0) AslLineGit.Modified else AslLineGit.Added) }
                    } else if (removed > 0) {
                        put(newCursor.coerceAtLeast(0), AslLineGit.Deleted)
                    }
                    removed = 0
                    added.clear()
                }
                hunk.lines.forEach { line ->
                    when (line.kind) {
                        GitDiffKind.REMOVED -> removed++
                        GitDiffKind.ADDED, GitDiffKind.MODIFIED -> {
                            line.newNo?.let { added += it - 1; newCursor = it }
                        }
                        GitDiffKind.CONTEXT -> {
                            flushEdit()
                            newCursor = line.newNo ?: newCursor
                        }
                    }
                }
                flushEdit()
            }
        }
    }
}
