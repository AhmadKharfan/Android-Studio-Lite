package com.ahmadkharfan.androidstudiolite.domain.model

/** A local or remote branch. [current] is true for the checked-out branch. */
data class GitBranch(
    val name: String,
    val isRemote: Boolean = false,
    val current: Boolean = false,
)

/** One entry in the commit log. [timeMillis] is the commit time (epoch millis). */
data class GitCommit(
    val id: String,
    val shortId: String,
    val message: String,
    val authorName: String,
    val authorEmail: String,
    val timeMillis: Long,
)

enum class GitRefKind { BRANCH, REMOTE, TAG, HEAD }

data class GitRefLabel(
    val name: String,
    val kind: GitRefKind,
)

/** Commit row used by repository and file-history paging. [path] tracks historical renames. */
data class GitCommitSummary(
    val id: String,
    val shortId: String,
    val message: String,
    val fullMessage: String,
    val authorName: String,
    val authorEmail: String,
    val authorTimeMillis: Long,
    val parents: List<String>,
    val refs: List<GitRefLabel> = emptyList(),
    val isShallowBoundary: Boolean = false,
    val path: String? = null,
)

data class GitLogPage(
    val commits: List<GitCommitSummary>,
    /** Pass this value back as `cursor` to continue strictly after the last returned commit. */
    val nextCursor: String?,
)

data class GitCommitIdentity(
    val name: String,
    val email: String,
    val timeMillis: Long,
)

enum class GitCommitChangeType { ADDED, MODIFIED, DELETED, RENAMED, COPIED }

data class GitCommitFileChange(
    val path: String,
    val oldPath: String? = null,
    val type: GitCommitChangeType,
)

data class GitCommitDetails(
    val id: String,
    val shortId: String,
    val fullMessage: String,
    val author: GitCommitIdentity,
    val committer: GitCommitIdentity,
    val parents: List<String>,
    val refs: List<GitRefLabel>,
    val changedFiles: List<GitCommitFileChange>,
    val isShallowBoundary: Boolean = false,
)

data class GitBlameLine(
    val lineNo: Int,
    val commitId: String,
    val shortId: String,
    val authorName: String,
    val authorTimeMillis: Long,
    val lineText: String,
)

data class GitTag(
    val name: String,
    val target: String,
    val annotated: Boolean,
    val message: String? = null,
)

data class GitStash(
    val index: Int,
    val id: String,
    val message: String,
    val timeMillis: Long,
)

/**
 * HTTPS credentials for a remote. For GitHub-style personal access tokens the token is sent as the
 * password; [username] may be any non-blank value (GitHub ignores it for PAT auth) and defaults to
 * `x-access-token` when left blank.
 */
data class GitCredentials(
    val username: String,
    val token: String,
)

/** A configured Git remote. URLs are always returned without embedded user-info. */
data class GitRemote(
    val name: String,
    val url: String,
    val pushUrl: String? = null,
)

/** Tracking configuration for a local [branch]. */
data class GitUpstream(
    val branch: String,
    val remote: String,
    val remoteBranch: String,
)

enum class PullMode { MERGE, REBASE }

enum class GitFastForwardMode { FF_ALLOWED, FF_ONLY, NO_FF }

enum class GitIntegrationStatus {
    FAST_FORWARD, MERGED, APPLIED, CONFLICTS, ALREADY_UP_TO_DATE, ABORTED_FF_ONLY, ABORTED,
}

data class GitIntegrationResult(
    val status: GitIntegrationStatus,
    val newHead: String? = null,
    val detail: String? = null,
)

data class GitConflictEntry(
    val path: String,
    val base: String?,
    val ours: String?,
    val theirs: String?,
    val worktree: String?,
)

enum class GitResetMode { SOFT, MIXED, HARD }

enum class GitSubmoduleStatus { UNINITIALIZED, INITIALIZED, CHECKED_OUT, MISSING }

data class GitSubmodule(
    val name: String,
    val path: String,
    val url: String,
    val headId: String? = null,
    val status: GitSubmoduleStatus,
)

/** Successful outcome of a push or pull. Failures are thrown as [GitException]. */
data class GitSyncResult(
    val success: Boolean,
    val detail: String,
)

/**
 * The remote a project can be built from server-side: the current branch's tracked remote URL and the
 * ref (branch name) to check out. Resolved from a working tree so a cloned project can build by Git
 * clone instead of zip-upload. Null when the directory isn't a git repo or has no configured remote.
 */
data class GitRemoteInfo(
    val url: String,
    val ref: String,
    /** True when the remote cannot safely be cloned by the build server without app-held secrets. */
    val requiresAuth: Boolean = false,
)
