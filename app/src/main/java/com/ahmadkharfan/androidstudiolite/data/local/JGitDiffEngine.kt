package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffHunk
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffKind
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffLine
import com.ahmadkharfan.androidstudiolite.domain.model.GitException
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileDiff
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.Edit
import org.eclipse.jgit.diff.HistogramDiff
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.diff.RawTextComparator
import org.eclipse.jgit.diff.RenameDetector
import org.eclipse.jgit.dircache.DirCacheEditor
import org.eclipse.jgit.dircache.DirCacheEntry
import org.eclipse.jgit.dircache.DirCacheIterator
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.AbstractTreeIterator
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.FileTreeIterator
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.WorkingTreeIterator
import org.eclipse.jgit.treewalk.filter.PathFilter
import org.eclipse.jgit.util.io.EolStreamTypeUtil
import java.io.ByteArrayOutputStream

/** Content-oriented Git diff implementation, deliberately independent from patch text formatting. */
internal class JGitDiffEngine {

    fun indexToWorktree(repo: Repository, path: String, force: Boolean): GitFileDiff {
        val index = DirCacheIterator(repo.readDirCache())
        val worktree = FileTreeIterator(repo)
        val entry = findEntry(repo, index, worktree, path, detectRenames = false)
        val oldPath = entry?.oldPath?.takeUnless { it == DiffEntry.DEV_NULL }
        val newPath = entry?.newPath?.takeUnless { it == DiffEntry.DEV_NULL } ?: path
        if (!force && (indexSize(repo, oldPath ?: path) > MAX_BYTES ||
                java.io.File(repo.workTree, newPath).let { it.isFile && it.length() > MAX_BYTES })) {
            return GitFileDiff(newPath, oldPath?.takeIf { it != newPath }, tooLarge = true)
        }
        val old = indexContent(repo, oldPath ?: path)
        val work = worktreeContent(repo, newPath)
        return createDiff(newPath, oldPath?.takeIf { it != newPath }, old, work.content, force, work.binaryByAttribute)
    }

    fun headToIndex(repo: Repository, path: String, force: Boolean): GitFileDiff {
        val head = headIterator(repo)
        val index = DirCacheIterator(repo.readDirCache())
        val entry = findEntry(repo, head, index, path)
        val oldPath = entry?.oldPath?.takeUnless { it == DiffEntry.DEV_NULL } ?: path
        val newPath = entry?.newPath?.takeUnless { it == DiffEntry.DEV_NULL } ?: path
        if (!force && (treeSize(repo, headIterator(repo), oldPath) > MAX_BYTES ||
                indexSize(repo, newPath) > MAX_BYTES)) {
            return GitFileDiff(newPath, oldPath.takeIf { it != newPath }, tooLarge = true)
        }
        return createDiff(
            path = newPath,
            oldPath = oldPath.takeIf { it != newPath },
            oldBytes = treeContent(repo, headIterator(repo), oldPath),
            newBytes = indexContent(repo, newPath),
            force = force,
            binaryByAttribute = binaryByAttribute(repo, newPath),
        )
    }

    fun commitToParent(repo: Repository, commitId: String, path: String, force: Boolean): GitFileDiff {
        RevWalk(repo).use { walk ->
            val commit = walk.parseCommit(repo.resolve(commitId) ?: throw GitException.Unknown("Unknown commit: $commitId"))
            val parentTree = commit.parents.firstOrNull()?.let { walk.parseCommit(it).tree }
            val oldIterator = parentTree?.let { CanonicalTreeParser(null, repo.newObjectReader(), it) }
                ?: EmptyTreeIterator()
            val newIterator = CanonicalTreeParser(null, repo.newObjectReader(), commit.tree)
            val entry = findEntry(repo, oldIterator, newIterator, path)
            val oldPath = entry?.oldPath?.takeUnless { it == DiffEntry.DEV_NULL } ?: path
            val newPath = entry?.newPath?.takeUnless { it == DiffEntry.DEV_NULL } ?: path
            if (!force && (treeSize(repo, parentTree?.let {
                    CanonicalTreeParser(null, repo.newObjectReader(), it)
                } ?: EmptyTreeIterator(), oldPath) > MAX_BYTES ||
                    treeSize(repo, CanonicalTreeParser(null, repo.newObjectReader(), commit.tree), newPath) > MAX_BYTES)) {
                return GitFileDiff(newPath, oldPath.takeIf { it != newPath }, tooLarge = true)
            }
            return createDiff(
                newPath,
                oldPath.takeIf { it != newPath },
                treeContent(repo, parentTree?.let { CanonicalTreeParser(null, repo.newObjectReader(), it) }
                    ?: EmptyTreeIterator(), oldPath),
                treeContent(repo, CanonicalTreeParser(null, repo.newObjectReader(), commit.tree), newPath),
                force,
                binaryByAttribute(repo, newPath),
            )
        }
    }

    fun indexToBuffer(repo: Repository, path: String, buffer: String): GitFileDiff {
        val bytes = buffer.replace("\r\n", "\n").toByteArray()
        if (bytes.size > MAX_BYTES || indexSize(repo, path) > MAX_BYTES) {
            return GitFileDiff(path, tooLarge = true)
        }
        return createDiff(path, null, indexContent(repo, path), bytes, force = false)
    }

    fun updateIndex(repo: Repository, path: String, requested: GitDiffHunk, reverse: Boolean) {
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
        if (existing?.fileMode == FileMode.SYMLINK || (!reverse && worktreeMode(repo, path) == FileMode.SYMLINK)) {
            throw GitException.PartialStaging("Partial staging is not supported for symbolic links")
        }
        if (worktreeFilterCommand(repo, path) != null) {
            throw GitException.PartialStaging("Partial staging is not supported for files with Git clean filters")
        }

        val current = if (reverse) headToIndex(repo, path, force = false) else indexToWorktree(repo, path, force = false)
        if (current.isBinary || current.tooLarge) {
            throw GitException.PartialStaging("Partial staging is available only for text files under 512 KiB")
        }
        val hunk = current.hunks.firstOrNull {
            it.oldStart == requested.oldStart && it.oldCount == requested.oldCount &&
                it.newStart == requested.newStart && it.newCount == requested.newCount
        } ?: throw GitException.PartialStaging("The file changed; refresh the diff and try again")

        val indexBytes = indexContent(repo, path) ?: ByteArray(0)
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
        val keepNewline = when {
            original.isEmpty() -> false
            reverse -> headContent(repo, path)?.let { !RawText(it).isMissingNewlineAtEnd } ?: false
            else -> worktreeContent(repo, path).content?.let { !RawText(it).isMissingNewlineAtEnd } ?: false
        }
        val bytes = buildString {
            append(original.joinToString("\n"))
            if (keepNewline) append('\n')
        }.toByteArray()
        writeIndex(repo, path, existing, bytes, existing?.fileMode ?: worktreeMode(repo, path) ?: FileMode.REGULAR_FILE)
    }

    private fun createDiff(
        path: String,
        oldPath: String?,
        oldBytes: ByteArray?,
        newBytes: ByteArray?,
        force: Boolean,
        binaryByAttribute: Boolean = false,
    ): GitFileDiff {
        val old = oldBytes ?: ByteArray(0)
        val new = newBytes ?: ByteArray(0)
        val binary = binaryByAttribute || RawText.isBinary(old) || RawText.isBinary(new)
        if (binary) return GitFileDiff(path, oldPath, isBinary = true)
        if (!force && (old.size > MAX_BYTES || new.size > MAX_BYTES)) {
            return GitFileDiff(path, oldPath, tooLarge = true)
        }
        val oldText = RawText(old)
        val newText = RawText(new)
        if (!force && (oldText.size() > MAX_LINES || newText.size() > MAX_LINES)) {
            return GitFileDiff(path, oldPath, tooLarge = true)
        }
        val edits = HistogramDiff().apply { setFallbackAlgorithm(null) }
            .diff(RawTextComparator.DEFAULT, oldText, newText)
        return GitFileDiff(path, oldPath, hunks = edits.toHunks(oldText, newText))
    }

    private fun List<Edit>.toHunks(old: RawText, new: RawText): List<GitDiffHunk> {
        if (isEmpty()) return emptyList()
        val regions = mutableListOf<IntArray>()
        for (edit in this) {
            val region = intArrayOf(
                (edit.beginA - CONTEXT).coerceAtLeast(0),
                (edit.endA + CONTEXT).coerceAtMost(old.size()),
                (edit.beginB - CONTEXT).coerceAtLeast(0),
                (edit.endB + CONTEXT).coerceAtMost(new.size()),
            )
            val previous = regions.lastOrNull()
            if (previous != null && region[0] <= previous[1]) {
                previous[1] = maxOf(previous[1], region[1])
                previous[3] = maxOf(previous[3], region[3])
            } else {
                regions += region
            }
        }
        return regions.map { region ->
            val relevant = filter { it.endA >= region[0] && it.beginA <= region[1] }
            val lines = mutableListOf<GitDiffLine>()
            var a = region[0]
            var b = region[2]
            relevant.forEach { edit ->
                while (a < edit.beginA && b < edit.beginB) {
                    lines += GitDiffLine(GitDiffKind.CONTEXT, old.getString(a), a + 1, b + 1)
                    a++; b++
                }
                while (a < edit.endA) lines += GitDiffLine(GitDiffKind.REMOVED, old.getString(a), oldNo = ++a)
                while (b < edit.endB) lines += GitDiffLine(GitDiffKind.ADDED, new.getString(b), newNo = ++b)
            }
            while (a < region[1] && b < region[3]) {
                lines += GitDiffLine(GitDiffKind.CONTEXT, old.getString(a), a + 1, b + 1)
                a++; b++
            }
            val oldCount = region[1] - region[0]
            val newCount = region[3] - region[2]
            GitDiffHunk(
                oldStart = if (oldCount == 0) 0 else region[0] + 1,
                oldCount = oldCount,
                newStart = if (newCount == 0) 0 else region[2] + 1,
                newCount = newCount,
                lines = lines,
            )
        }
    }
    private fun findEntry(
        repo: Repository,
        oldTree: AbstractTreeIterator,
        newTree: AbstractTreeIterator,
        path: String,
        detectRenames: Boolean = true,
    ): DiffEntry? {
        val entries = DiffFormatter(ByteArrayOutputStream()).use { formatter ->
            formatter.setRepository(repo)
            if (!detectRenames) formatter.pathFilter = PathFilter.create(path)
            formatter.scan(oldTree, newTree)
        }
        val detected = if (detectRenames && entries.size <= RENAME_LIMIT) {
            RenameDetector(repo).apply { addAll(entries) }.compute()
        } else {
            entries
        }
        return detected.firstOrNull { it.newPath == path || it.oldPath == path }
    }
    private fun treeContent(repo: Repository, tree: AbstractTreeIterator, path: String): ByteArray? {
        TreeWalk(repo).use { walk ->
            walk.addTree(tree)
            walk.isRecursive = true
            walk.filter = PathFilter.create(path)
            if (!walk.next() || walk.pathString != path || walk.getFileMode(0) == FileMode.MISSING) return null
            return repo.open(walk.getObjectId(0), Constants.OBJ_BLOB).bytes
        }
    }

    private fun treeSize(repo: Repository, tree: AbstractTreeIterator, path: String): Long {
        TreeWalk(repo).use { walk ->
            walk.addTree(tree)
            walk.isRecursive = true
            walk.filter = PathFilter.create(path)
            if (!walk.next() || walk.pathString != path || walk.getFileMode(0) == FileMode.MISSING) return 0
            return repo.open(walk.getObjectId(0), Constants.OBJ_BLOB).size
        }
    }

    private fun headContent(repo: Repository, path: String): ByteArray? = treeContent(repo, headIterator(repo), path)

    private fun indexContent(repo: Repository, path: String): ByteArray? {
        val entry = repo.readDirCache().getEntry(path) ?: return null
        if (entry.stage != DirCacheEntry.STAGE_0) return null
        return repo.open(entry.objectId, Constants.OBJ_BLOB).bytes
    }

    private fun indexSize(repo: Repository, path: String): Long {
        val entry = repo.readDirCache().getEntry(path) ?: return 0
        if (entry.stage != DirCacheEntry.STAGE_0) return 0
        return repo.open(entry.objectId, Constants.OBJ_BLOB).size
    }

    private data class WorktreeContent(
        val content: ByteArray?,
        val filterCommand: String? = null,
        val binaryByAttribute: Boolean = false,
    )

    private fun worktreeContent(repo: Repository, path: String): WorktreeContent {
        TreeWalk(repo).use { walk ->
            walk.operationType = TreeWalk.OperationType.CHECKIN_OP
            walk.addTree(DirCacheIterator(repo.readDirCache()))
            walk.addTree(FileTreeIterator(repo))
            walk.isRecursive = true
            walk.filter = PathFilter.create(path)
            if (!walk.next() || walk.pathString != path || walk.getFileMode(1) == FileMode.MISSING) {
                return WorktreeContent(null)
            }
            val iterator = walk.getTree(1, WorkingTreeIterator::class.java)
            val filter = iterator.cleanFilterCommand?.takeIf { it.isNotBlank() }
            val binary = walk.attributes.isUnset("diff") || walk.attributes.isSet("binary")
            val content = EolStreamTypeUtil.wrapInputStream(iterator.openEntryStream(), iterator.eolStreamType)
                .use { it.readBytes() }
            return WorktreeContent(content, filter, binary)
        }
    }

    private fun worktreeMode(repo: Repository, path: String): FileMode? {
        TreeWalk(repo).use { walk ->
            walk.addTree(FileTreeIterator(repo))
            walk.isRecursive = true
            walk.filter = PathFilter.create(path)
            return if (walk.next() && walk.pathString == path) walk.getFileMode(0) else null
        }
    }

    private fun writeIndex(repo: Repository, path: String, existing: DirCacheEntry?, bytes: ByteArray, mode: FileMode) {
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

    private fun headIterator(repo: Repository): AbstractTreeIterator {
        val head = repo.resolve("HEAD^{tree}") ?: return EmptyTreeIterator()
        return CanonicalTreeParser(null, repo.newObjectReader(), head)
    }

    private fun Int.toZeroBased(lineCount: Int): Int = if (this == 0 && lineCount == 0) 0 else (this - 1).coerceAtLeast(0)


    private fun binaryByAttribute(repo: Repository, path: String): Boolean {
        TreeWalk(repo).use { walk ->
            walk.addTree(FileTreeIterator(repo))
            walk.isRecursive = true
            walk.filter = PathFilter.create(path)
            if (!walk.next() || walk.pathString != path) return false
            return walk.attributes.isUnset("diff") || walk.attributes.isSet("binary")
        }
    }

    private fun worktreeFilterCommand(repo: Repository, path: String): String? =
        worktreeContent(repo, path).filterCommand
    private companion object {
        const val MAX_BYTES = 512 * 1024
        const val MAX_LINES = 20_000
        const val CONTEXT = 3
        const val RENAME_LIMIT = 2_000
    }
}
