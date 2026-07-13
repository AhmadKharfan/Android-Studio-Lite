package com.ahmadkharfan.androidstudiolite.domain.model

/**
 * A single path with a pending change in the working tree or index.
 *
 * @param staged true when the change is in the index (staged for the next commit); false when it is
 * only in the working tree.
 */
data class GitChange(
    val path: String,
    val status: GitFileStatus,
    val staged: Boolean = false,
)

enum class GitDiffKind { ADDED, REMOVED, MODIFIED, CONTEXT }

data class GitDiffLine(
    val kind: GitDiffKind,
    val text: String,
    val oldNo: Int? = null,
    val newNo: Int? = null,
)

/**
 * Snapshot of a repository's status surfaced to the UI.
 *
 * @param branch short name of the currently checked-out branch, or a detached-HEAD label.
 * @param changes staged + unstaged changes; an untracked/added path can appear once as unstaged and,
 * after staging, once as staged.
 * @param ahead/behind commit counts relative to the upstream tracking branch (null when there is no
 * upstream configured).
 * @param isRepository false when the directory is not (yet) a git working tree; the panel renders an
 * empty state in that case rather than error.
 */
data class GitState(
    val branch: String,
    val changes: List<GitChange>,
    val commitMessage: String = "",
    val committing: Boolean = false,
    val ahead: Int? = null,
    val behind: Int? = null,
    val isRepository: Boolean = true,
)
