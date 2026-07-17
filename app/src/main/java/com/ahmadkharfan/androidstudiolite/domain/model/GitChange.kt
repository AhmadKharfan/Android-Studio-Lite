package com.ahmadkharfan.androidstudiolite.domain.model

enum class GitIndexStatus { ADDED, MODIFIED, DELETED, RENAMED, UNCHANGED }

enum class GitWorktreeStatus { MODIFIED, DELETED, UNTRACKED, IGNORED, UNCHANGED }

enum class GitConflictStage { BASE, OURS, THEIRS }

/** Index stages retained for an unresolved path, plus JGit's human-readable conflict kind. */
data class GitConflictInfo(
    val conflicted: Boolean = true,
    val stages: Set<GitConflictStage>,
    val description: String,
)

/**
 * Layered status for one repository-relative path. Index and working-tree dimensions are independent:
 * a staged file edited again is both index-modified and worktree-modified.
 */
data class GitFileState(
    val path: String,
    val oldPath: String? = null,
    val indexStatus: GitIndexStatus = GitIndexStatus.UNCHANGED,
    val worktreeStatus: GitWorktreeStatus = GitWorktreeStatus.UNCHANGED,
    val conflictStage: GitConflictInfo? = null,
) {
    val hasPendingChange: Boolean get() = conflictStage != null ||
        indexStatus != GitIndexStatus.UNCHANGED ||
        worktreeStatus !in setOf(GitWorktreeStatus.UNCHANGED, GitWorktreeStatus.IGNORED)
}

enum class GitRepositoryState { SAFE, MERGING, REBASING, CHERRY_PICKING, REVERTING, BISECTING }

sealed interface GitHeadState {
    data class Branch(val name: String) : GitHeadState
    data class Detached(val shortSha: String) : GitHeadState
    data object Unborn : GitHeadState
}

data class GitAheadBehind(val ahead: Int, val behind: Int)

enum class GitDiffKind { ADDED, REMOVED, MODIFIED, CONTEXT }

enum class GitDiffTarget { INDEX_TO_WORKTREE, HEAD_TO_INDEX, COMMIT_TO_PARENT }

data class GitDiffLine(
    val kind: GitDiffKind,
    val text: String,
    val oldNo: Int? = null,
    val newNo: Int? = null,
)

/** A contiguous diff region, including the small amount of context needed to render it. */
data class GitDiffHunk(
    val oldStart: Int,
    val oldCount: Int,
    val newStart: Int,
    val newCount: Int,
    val lines: List<GitDiffLine>,
)

/** Structured content diff shared by the panel, full-screen viewer, and editor gutter. */
data class GitFileDiff(
    val path: String,
    val oldPath: String? = null,
    val isBinary: Boolean = false,
    val tooLarge: Boolean = false,
    val hunks: List<GitDiffHunk> = emptyList(),
)

/**
 * Snapshot of a repository's status surfaced to the UI.
 *
 * [files] is the authoritative layered status consumed by editor chrome and the VCS panel.
 * @param isRepository false when the directory is not (yet) a git working tree; the panel renders an
 * empty state in that case rather than error.
 */
data class GitState(
    val files: List<GitFileState>,
    val repositoryState: GitRepositoryState = GitRepositoryState.SAFE,
    val headState: GitHeadState = GitHeadState.Unborn,
    val aheadBehind: GitAheadBehind? = null,
    val commitMessage: String = "",
    val committing: Boolean = false,
    val isRepository: Boolean = true,
) {
    val branch: String get() = when (val head = headState) {
        is GitHeadState.Branch -> head.name
        is GitHeadState.Detached -> "HEAD (${head.shortSha})"
        GitHeadState.Unborn -> "HEAD"
    }

    val ahead: Int? get() = aheadBehind?.ahead
    val behind: Int? get() = aheadBehind?.behind
}
