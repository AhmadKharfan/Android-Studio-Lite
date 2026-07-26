package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffHunk
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffKind
import com.ahmadkharfan.androidstudiolite.domain.model.GitException
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.dircache.DirCacheEditor
import org.eclipse.jgit.dircache.DirCacheEntry
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.Repository

internal class JGitIndexEditor(
    private val diffReader: JGitDiffReader,
) {

    fun updateIndex(repo: Repository, path: String, requested: GitDiffHunk, reverse: Boolean) {
        val existing = validateIndexState(repo, path, reverse)
        val hunk = currentHunk(repo, path, requested, reverse)
        val updatedLines = applyHunkToIndex(repo, path, hunk, reverse)
        val bytes = serializeIndexContent(repo, path, updatedLines, reverse)
        val mode = existing?.fileMode ?: diffReader.worktreeMode(repo, path) ?: FileMode.REGULAR_FILE
        writeIndex(repo, path, existing, bytes, mode)
    }

    private fun currentHunk(
        repo: Repository,
        path: String,
        requested: GitDiffHunk,
        reverse: Boolean,
    ): GitDiffHunk {
        val current = if (reverse) {
            diffReader.headToIndex(repo, path, force = false)
        } else {
            diffReader.indexToWorktree(repo, path, force = false)
        }
        if (current.isBinary || current.tooLarge) {
            throw GitException.PartialStaging("Partial staging is available only for text files under 512 KiB")
        }
        return current.hunks.firstOrNull {
            it.oldStart == requested.oldStart && it.oldCount == requested.oldCount &&
                it.newStart == requested.newStart && it.newCount == requested.newCount
        } ?: throw GitException.PartialStaging("The file changed; refresh the diff and try again")
    }

    private fun applyHunkToIndex(
        repo: Repository,
        path: String,
        hunk: GitDiffHunk,
        reverse: Boolean,
    ): List<String> {
        val indexBytes = diffReader.indexContent(repo, path) ?: ByteArray(0)
        val indexText = RawText(indexBytes)
        val start = if (reverse) hunk.newStart.toZeroBased(indexText.size()) else hunk.oldStart.toZeroBased(indexText.size())
        val count = if (reverse) hunk.newCount else hunk.oldCount
        val replacement = hunk.lines.filter { line ->
            if (reverse) line.kind != GitDiffKind.ADDED else line.kind != GitDiffKind.REMOVED
        }.map { it.text }
        val original = (0 until indexText.size()).map(indexText::getString).toMutableList()
        if (start !in 0..original.size || start + count > original.size) {
            throw GitException.PartialStaging("The index changed; refresh the diff and try again")
        }
        repeat(count) { original.removeAt(start) }
        original.addAll(start, replacement)
        return original
    }

    private fun validateIndexState(repo: Repository, path: String, reverse: Boolean): DirCacheEntry? {
        val cacheSnapshot = repo.readDirCache()
        val firstEntry = cacheSnapshot.findEntry(path)
        val hasConflictStages = firstEntry >= 0 &&
            (firstEntry until cacheSnapshot.nextEntry(firstEntry)).any {
                cacheSnapshot.getEntry(it).stage != DirCacheEntry.STAGE_0
            }
        if (hasConflictStages) {
            throw GitException.PartialStaging("Resolve conflicts before staging individual hunks")
        }
        val existing = cacheSnapshot.getEntry(path)
        if (existing?.fileMode == FileMode.SYMLINK ||
            (!reverse && diffReader.worktreeMode(repo, path) == FileMode.SYMLINK)
        ) {
            throw GitException.PartialStaging("Partial staging is not supported for symbolic links")
        }
        if (diffReader.worktreeFilterCommand(repo, path) != null) {
            throw GitException.PartialStaging("Partial staging is not supported for files with Git clean filters")
        }
        return existing
    }

    private fun serializeIndexContent(
        repo: Repository,
        path: String,
        lines: List<String>,
        reverse: Boolean,
    ): ByteArray {
        val keepNewline = when {
            lines.isEmpty() -> false
            reverse -> diffReader.headContent(repo, path)?.let { !RawText(it).isMissingNewlineAtEnd } ?: false
            else -> diffReader.worktreeContent(repo, path).content?.let {
                !RawText(it).isMissingNewlineAtEnd
            } ?: false
        }
        return buildString {
            append(lines.joinToString("\n"))
            if (keepNewline) append('\n')
        }.toByteArray()
    }

    private fun writeIndex(
        repo: Repository,
        path: String,
        existing: DirCacheEntry?,
        bytes: ByteArray,
        mode: FileMode,
    ) {
        val cache = repo.lockDirCache()
        try {
            val editor = cache.editor()
            if (bytes.isEmpty() && existing != null && !java.io.File(repo.workTree, path).exists()) {
                editor.add(DirCacheEditor.DeletePath(path))
            } else {
                val objectId = repo.newObjectInserter().use { inserter ->
                    inserter.insert(Constants.OBJ_BLOB, bytes).also { inserter.flush() }
                }
                editor.add(object : DirCacheEditor.PathEdit(path) {
                    override fun apply(entry: DirCacheEntry) {
                        existing?.let(entry::copyMetaData)
                        entry.fileMode = mode
                        entry.setObjectId(objectId)
                        entry.length = bytes.size
                    }
                })
            }
            check(editor.commit()) { "Couldn't update Git index" }
        } finally {
            cache.unlock()
        }
    }

    private fun Int.toZeroBased(lineCount: Int): Int =
        if (this == 0 && lineCount == 0) 0 else (this - 1).coerceAtLeast(0)
}
