package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.GitAheadBehind
import com.ahmadkharfan.androidstudiolite.domain.model.GitConflictInfo
import com.ahmadkharfan.androidstudiolite.domain.model.GitConflictStage
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileState
import com.ahmadkharfan.androidstudiolite.domain.model.GitHeadState
import com.ahmadkharfan.androidstudiolite.domain.model.GitIndexStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitRepositoryState
import com.ahmadkharfan.androidstudiolite.domain.model.GitState
import com.ahmadkharfan.androidstudiolite.domain.model.GitWorktreeStatus
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.RenameDetector
import org.eclipse.jgit.dircache.DirCacheIterator
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.IndexDiff.StageState
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.FileTreeIterator
import org.eclipse.jgit.util.io.DisabledOutputStream

internal class JGitStatusComputer {
    fun compute(
        repoDir: File,
        includeIgnored: Boolean,
        commitMessage: String,
        monitor: ProgressMonitor,
    ): GitState {
        val repository = openRepositoryOrNull(repoDir)
            ?: return GitState(files = emptyList(), commitMessage = commitMessage, isRepository = false)
        repository.use { repo ->
            Git(repo).use { git ->
                val status = git.status()
                    .setWorkingTreeIt(FileTreeIterator(repo))
                    .setProgressMonitor(monitor)
                    .call()
                val files = linkedMapOf<String, MutableGitFileState>()

                status.added.forEach { files.file(it).indexStatus = GitIndexStatus.ADDED }
                status.changed.forEach { files.file(it).indexStatus = GitIndexStatus.MODIFIED }
                status.removed.forEach { files.file(it).indexStatus = GitIndexStatus.DELETED }
                status.modified.forEach { files.file(it).worktreeStatus = GitWorktreeStatus.MODIFIED }
                status.missing.forEach { files.file(it).worktreeStatus = GitWorktreeStatus.DELETED }
                status.untracked.forEach { files.file(it).worktreeStatus = GitWorktreeStatus.UNTRACKED }

                if (includeIgnored) {
                    status.ignoredNotInIndex
                        .asSequence()
                        .map { it.trimEnd('/') }
                        .filter { it.isNotBlank() && '/' !in it }
                        .forEach { files.file(it).worktreeStatus = GitWorktreeStatus.IGNORED }
                }

                status.conflicting.forEach { path ->
                    val stage = status.conflictingStageState[path]
                    files.file(path).conflictStage = stage?.toConflictInfo()
                        ?: GitConflictInfo(stages = emptySet(), description = "Unresolved conflict")
                }

                val stagedRenames = if (status.conflicting.isEmpty()) detectStagedRenames(repo, monitor) else emptyList()
                stagedRenames.forEach { rename ->
                    val renamed = files.file(rename.newPath)
                    renamed.oldPath = rename.oldPath
                    renamed.indexStatus = GitIndexStatus.RENAMED
                    files[rename.oldPath]?.let { old ->
                        if (old.worktreeStatus == GitWorktreeStatus.UNCHANGED && old.conflictStage == null) {
                            files.remove(rename.oldPath)
                        }
                    }
                }

                val headState = repo.headState()
                val tracking = (headState as? GitHeadState.Branch)?.let { branch ->
                    runCatching { BranchTrackingStatus.of(repo, branch.name) }.getOrNull()
                }
                return GitState(
                    files = files.values.map(MutableGitFileState::toImmutable).sortedBy { it.path },
                    repositoryState = repo.repositoryState.toDomainState(),
                    headState = headState,
                    aheadBehind = tracking?.let { GitAheadBehind(it.aheadCount, it.behindCount) },
                    commitMessage = commitMessage,
                    isRepository = true,
                )
            }
        }
    }

    private fun detectStagedRenames(repo: Repository, monitor: ProgressMonitor): List<DiffEntry> {
        val entries = DiffFormatter(DisabledOutputStream.INSTANCE).use { formatter ->
            formatter.setRepository(repo)
            formatter.scan(headTreeIterator(repo), DirCacheIterator(repo.readDirCache()))
        }
        if (entries.size > RENAME_DETECTION_LIMIT) return emptyList()
        return RenameDetector(repo).apply { addAll(entries) }
            .compute(monitor)
            .filter { it.changeType == DiffEntry.ChangeType.RENAME }
    }

    private fun Repository.headState(): GitHeadState {
        val head = resolve(Constants.HEAD) ?: return GitHeadState.Unborn
        val fullBranch = fullBranch
        return if (fullBranch?.startsWith(Constants.R_HEADS) == true) {
            GitHeadState.Branch(Repository.shortenRefName(fullBranch))
        } else {
            GitHeadState.Detached(head.abbreviate(7).name())
        }
    }

    private fun RepositoryState.toDomainState(): GitRepositoryState = when {
        isRebasing -> GitRepositoryState.REBASING
        this == RepositoryState.MERGING || this == RepositoryState.MERGING_RESOLVED -> GitRepositoryState.MERGING
        this == RepositoryState.CHERRY_PICKING || this == RepositoryState.CHERRY_PICKING_RESOLVED ->
            GitRepositoryState.CHERRY_PICKING
        this == RepositoryState.REVERTING || this == RepositoryState.REVERTING_RESOLVED ->
            GitRepositoryState.REVERTING
        this == RepositoryState.BISECTING -> GitRepositoryState.BISECTING
        else -> GitRepositoryState.SAFE
    }

    private fun StageState.toConflictInfo(): GitConflictInfo {
        val stages = buildSet {
            if (hasBase()) add(GitConflictStage.BASE)
            if (hasOurs()) add(GitConflictStage.OURS)
            if (hasTheirs()) add(GitConflictStage.THEIRS)
        }
        return GitConflictInfo(
            stages = stages,
            description = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase),
        )
    }

    private fun headTreeIterator(repo: Repository): org.eclipse.jgit.treewalk.AbstractTreeIterator {
        val head = repo.resolve("HEAD^{tree}") ?: return EmptyTreeIterator()
        return CanonicalTreeParser(null, repo.newObjectReader(), head)
    }

    private fun MutableMap<String, MutableGitFileState>.file(path: String): MutableGitFileState =
        getOrPut(path) { MutableGitFileState(path) }

    private data class MutableGitFileState(
        val path: String,
        var oldPath: String? = null,
        var indexStatus: GitIndexStatus = GitIndexStatus.UNCHANGED,
        var worktreeStatus: GitWorktreeStatus = GitWorktreeStatus.UNCHANGED,
        var conflictStage: GitConflictInfo? = null,
    ) {
        fun toImmutable() = GitFileState(path, oldPath, indexStatus, worktreeStatus, conflictStage)
    }

    private companion object {
        const val RENAME_DETECTION_LIMIT = 2_000
    }
}

internal fun openRepositoryOrNull(repoDir: File): Repository? = runCatching {
    FileRepositoryBuilder()
        .readEnvironment()
        .findGitDir(repoDir)
        .apply { if (File(repoDir, Constants.DOT_GIT).isFile) setWorkTree(repoDir) }
        .setMustExist(true)
        .build()
}.getOrNull()
