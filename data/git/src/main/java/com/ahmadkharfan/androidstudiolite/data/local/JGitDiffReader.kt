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

internal class JGitDiffReader {

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
        return contextRegions(old, new).map { region -> toHunk(region, old, new) }
    }

    private fun List<Edit>.contextRegions(old: RawText, new: RawText): List<IntArray> {
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
        return regions
    }

    private fun List<Edit>.toHunk(region: IntArray, old: RawText, new: RawText): GitDiffHunk {
        val relevantEdits = filter { it.endA >= region[0] && it.beginA <= region[1] }
        val oldCount = region[1] - region[0]
        val newCount = region[3] - region[2]
        return GitDiffHunk(
            oldStart = if (oldCount == 0) 0 else region[0] + 1,
            oldCount = oldCount,
            newStart = if (newCount == 0) 0 else region[2] + 1,
            newCount = newCount,
            lines = collectHunkLines(region, relevantEdits, old, new),
        )
    }

    private fun collectHunkLines(
        region: IntArray,
        edits: List<Edit>,
        old: RawText,
        new: RawText,
    ): List<GitDiffLine> {
        val lines = mutableListOf<GitDiffLine>()
        var a = region[0]
        var b = region[2]
        edits.forEach { edit ->
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
        return lines
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

    internal fun headContent(repo: Repository, path: String): ByteArray? =
        treeContent(repo, headIterator(repo), path)

    internal fun indexContent(repo: Repository, path: String): ByteArray? {
        val entry = repo.readDirCache().getEntry(path) ?: return null
        if (entry.stage != DirCacheEntry.STAGE_0) return null
        return repo.open(entry.objectId, Constants.OBJ_BLOB).bytes
    }

    private fun indexSize(repo: Repository, path: String): Long {
        val entry = repo.readDirCache().getEntry(path) ?: return 0
        if (entry.stage != DirCacheEntry.STAGE_0) return 0
        return repo.open(entry.objectId, Constants.OBJ_BLOB).size
    }

    internal class WorktreeContent(
        val content: ByteArray?,
        val filterCommand: String? = null,
        val binaryByAttribute: Boolean = false,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WorktreeContent) return false
            return content.contentEquals(other.content) &&
                filterCommand == other.filterCommand &&
                binaryByAttribute == other.binaryByAttribute
        }

        override fun hashCode(): Int {
            var result = content?.contentHashCode() ?: 0
            result = 31 * result + (filterCommand?.hashCode() ?: 0)
            result = 31 * result + binaryByAttribute.hashCode()
            return result
        }
    }

    internal fun worktreeContent(repo: Repository, path: String): WorktreeContent {
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

    internal fun worktreeMode(repo: Repository, path: String): FileMode? {
        TreeWalk(repo).use { walk ->
            walk.addTree(FileTreeIterator(repo))
            walk.isRecursive = true
            walk.filter = PathFilter.create(path)
            return if (walk.next() && walk.pathString == path) walk.getFileMode(0) else null
        }
    }

    private fun headIterator(repo: Repository): AbstractTreeIterator {
        val head = repo.resolve("HEAD^{tree}") ?: return EmptyTreeIterator()
        return CanonicalTreeParser(null, repo.newObjectReader(), head)
    }

    private fun binaryByAttribute(repo: Repository, path: String): Boolean {
        TreeWalk(repo).use { walk ->
            walk.addTree(FileTreeIterator(repo))
            walk.isRecursive = true
            walk.filter = PathFilter.create(path)
            if (!walk.next() || walk.pathString != path) return false
            return walk.attributes.isUnset("diff") || walk.attributes.isSet("binary")
        }
    }

    internal fun worktreeFilterCommand(repo: Repository, path: String): String? =
        worktreeContent(repo, path).filterCommand
    private companion object {
        const val MAX_BYTES = 512 * 1024
        const val MAX_LINES = 20_000
        const val CONTEXT = 3
        const val RENAME_LIMIT = 2_000
    }
}
