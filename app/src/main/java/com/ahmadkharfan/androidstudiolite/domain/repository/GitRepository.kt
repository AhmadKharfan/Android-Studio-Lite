package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.CloneProgress
import com.ahmadkharfan.androidstudiolite.domain.model.CloneOptions
import com.ahmadkharfan.androidstudiolite.domain.model.GitBranch
import com.ahmadkharfan.androidstudiolite.domain.model.GitAuthorConfig
import com.ahmadkharfan.androidstudiolite.domain.model.GitAuthorConfigState
import com.ahmadkharfan.androidstudiolite.domain.model.GitCommit
import com.ahmadkharfan.androidstudiolite.domain.model.GitBlameLine
import com.ahmadkharfan.androidstudiolite.domain.model.GitCommitDetails
import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffHunk
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileDiff
import com.ahmadkharfan.androidstudiolite.domain.model.GitRemoteInfo
import com.ahmadkharfan.androidstudiolite.domain.model.GitLogPage
import com.ahmadkharfan.androidstudiolite.domain.model.GitRemote
import com.ahmadkharfan.androidstudiolite.domain.model.GitState
import com.ahmadkharfan.androidstudiolite.domain.model.GitStash
import com.ahmadkharfan.androidstudiolite.domain.model.GitSyncResult
import com.ahmadkharfan.androidstudiolite.domain.model.GitUpstream
import com.ahmadkharfan.androidstudiolite.domain.model.GitTag
import com.ahmadkharfan.androidstudiolite.domain.model.PullMode
import com.ahmadkharfan.androidstudiolite.domain.model.GitConflictEntry
import com.ahmadkharfan.androidstudiolite.domain.model.GitFastForwardMode
import com.ahmadkharfan.androidstudiolite.domain.model.GitIntegrationResult
import com.ahmadkharfan.androidstudiolite.domain.model.GitResetMode
import com.ahmadkharfan.androidstudiolite.domain.model.GitSubmodule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Git operations over a real on-disk working tree. Every mutating call is scoped to a repository
 * root ([repoDir]). Implementations run all I/O off the main thread and surface failures as thrown
 * exceptions (callers use [com.ahmadkharfan.androidstudiolite.core.BaseViewModel.tryToExecute]).
 */
interface GitRepository {

    /**
     * Clone [url] into the caller-owned [destination], emitting transport progress. Project
     * registration is deliberately handled outside this repository. [credentials] are used for
     * private HTTPS remotes and, when non-null, persisted for later fetch/push.
     */
    fun clone(
        url: String,
        destination: File,
        options: CloneOptions,
        credentials: GitCredentials?,
    ): Flow<CloneProgress>

    /** Hot state of the repository status, updated on [refresh] and after mutating calls. */
    fun observeState(repoDir: File): StateFlow<GitState>

    /** Recompute status for [repoDir] and push it to [observeState] subscribers. */
    suspend fun refresh(repoDir: File, includeIgnored: Boolean = false)

    /** Immediate refresh used when the app returns to the foreground. */
    suspend fun onAppForegrounded(repoDir: File) = refresh(repoDir)

    /** Unstaged content difference for [path], with large content rendered only when [force] is true. */
    suspend fun diffIndexToWorktree(repoDir: File, path: String, force: Boolean = false): GitFileDiff

    /** Staged content difference for [path]. */
    suspend fun diffHeadToIndex(repoDir: File, path: String, force: Boolean = false): GitFileDiff

    /** Change introduced by [commitId] relative to its first parent. */
    suspend fun diffCommitToParent(
        repoDir: File,
        commitId: String,
        path: String,
        force: Boolean = false,
    ): GitFileDiff

    /** Diff unsaved editor text against the index without writing the buffer to disk. */
    suspend fun diffIndexToBuffer(repoDir: File, path: String, buffer: String): GitFileDiff

    /** Apply an index-to-worktree [hunk] to the index, leaving the working tree untouched. */
    suspend fun stageHunk(repoDir: File, path: String, hunk: GitDiffHunk)

    /** Reverse a HEAD-to-index [hunk] from the index, leaving the working tree untouched. */
    suspend fun unstageHunk(repoDir: File, path: String, hunk: GitDiffHunk)

    /** Stage [path] (git add), including deletions. */
    suspend fun stage(repoDir: File, path: String)

    /** Unstage [path] (git reset HEAD -- path), keeping working-tree contents. */
    suspend fun unstage(repoDir: File, path: String)

    suspend fun stageAll(repoDir: File)

    suspend fun unstageAll(repoDir: File)

    /** Remember the in-progress commit message so it survives status refreshes. */
    suspend fun setCommitMessage(repoDir: File, message: String)

    /** Commit exactly the staged changes, or amend `HEAD` when [amend] is true. Returns the id. */
    suspend fun commit(repoDir: File, amend: Boolean = false): String

    suspend fun getAuthorConfig(repoDir: File): GitAuthorConfigState

    suspend fun setLocalAuthor(repoDir: File, config: GitAuthorConfig?)

    suspend fun setAppGlobalAuthor(config: GitAuthorConfig)

    suspend fun branches(repoDir: File): List<GitBranch>

    /** Create [name]; when [checkout] is true, switch to it. */
    suspend fun createBranch(repoDir: File, name: String, checkout: Boolean = true)

    suspend fun checkout(repoDir: File, name: String)

    suspend fun checkoutRemoteBranch(repoDir: File, remoteBranch: String, localName: String? = null)

    suspend fun renameBranch(repoDir: File, oldName: String, newName: String)

    suspend fun deleteBranch(repoDir: File, name: String, force: Boolean = false)

    suspend fun publishBranch(repoDir: File, name: String): GitSyncResult

    suspend fun log(repoDir: File, max: Int = 50): List<GitCommit>

    suspend fun log(repoDir: File, cursor: String?, limit: Int): GitLogPage

    suspend fun fileHistory(repoDir: File, path: String, cursor: String?, limit: Int): GitLogPage

    suspend fun commitDetails(repoDir: File, commitId: String): GitCommitDetails

    suspend fun blame(repoDir: File, path: String): List<GitBlameLine>

    suspend fun isShallow(repoDir: File): Boolean

    suspend fun deepen(repoDir: File): GitSyncResult

    suspend fun listTags(repoDir: File): List<GitTag>

    suspend fun createTag(repoDir: File, name: String, message: String? = null, targetCommit: String? = null)

    suspend fun deleteTag(repoDir: File, name: String)

    suspend fun pushTag(repoDir: File, name: String): GitSyncResult

    suspend fun pushAllTags(repoDir: File): GitSyncResult

    suspend fun stashCreate(repoDir: File, message: String? = null, includeUntracked: Boolean = false): String?

    suspend fun stashList(repoDir: File): List<GitStash>

    suspend fun stashApply(repoDir: File, index: Int)

    suspend fun stashPop(repoDir: File, index: Int)

    suspend fun stashDrop(repoDir: File, index: Int)

    suspend fun merge(repoDir: File, ref: String, ffMode: GitFastForwardMode = GitFastForwardMode.FF_ALLOWED, message: String? = null): GitIntegrationResult
    suspend fun mergeAbort(repoDir: File)
    suspend fun cherryPick(repoDir: File, commitId: String): GitIntegrationResult
    suspend fun cherryPickAbort(repoDir: File)
    suspend fun revert(repoDir: File, commitId: String): GitIntegrationResult
    suspend fun revertAbort(repoDir: File)
    suspend fun rebase(repoDir: File, upstreamRef: String): GitIntegrationResult
    suspend fun rebaseContinue(repoDir: File): GitIntegrationResult
    suspend fun rebaseSkip(repoDir: File): GitIntegrationResult
    suspend fun rebaseAbort(repoDir: File): GitIntegrationResult

    suspend fun conflictEntries(repoDir: File): List<GitConflictEntry>
    suspend fun resolveAcceptOurs(repoDir: File, path: String)
    suspend fun resolveAcceptTheirs(repoDir: File, path: String)
    suspend fun markResolved(repoDir: File, path: String)

    suspend fun restoreFiles(repoDir: File, paths: List<String>)
    suspend fun reset(repoDir: File, commitId: String, mode: GitResetMode)
    suspend fun clean(repoDir: File, dryRun: Boolean, includeIgnored: Boolean = false): List<String>

    suspend fun submodules(repoDir: File): List<GitSubmodule>
    suspend fun submoduleInit(repoDir: File)
    suspend fun submoduleUpdate(repoDir: File)

    suspend fun bootstrapRepository(repoDir: File, initialCommitMessage: String? = null): String?
    suspend fun addToGitignore(repoDir: File, path: String)

    suspend fun listRemotes(repoDir: File): List<GitRemote>

    suspend fun addRemote(repoDir: File, name: String, url: String)

    suspend fun setRemoteUrl(repoDir: File, name: String, url: String)

    suspend fun removeRemote(repoDir: File, name: String)

    suspend fun fetch(repoDir: File, remote: String? = null, prune: Boolean = false): GitSyncResult

    suspend fun setUpstream(repoDir: File, branch: String, remote: String, remoteBranch: String)

    suspend fun upstreamOf(repoDir: File, branch: String): GitUpstream?

    /** Push the current branch to its remote, resolving stored credentials for the remote. */
    suspend fun push(repoDir: File, setUpstreamIfMissing: Boolean = true): GitSyncResult

    /** Force push guarded by the currently-known remote-tracking ref. Never fetches implicitly. */
    suspend fun pushForceWithLease(repoDir: File): GitSyncResult

    /** Pull (fetch + merge) the current branch from its remote. */
    suspend fun pull(repoDir: File, mode: PullMode = PullMode.MERGE): GitSyncResult

    suspend fun isRepository(repoDir: File): Boolean

    /**
     * The remote URL + current branch to build [repoDir] from server-side (via Git clone), or null
     * when [repoDir] isn't a repo, has no HEAD branch (detached/unborn), or the branch's remote has no
     * URL. Prefers the current branch's tracking remote, falling back to `origin`.
     */
    suspend fun remoteInfo(repoDir: File): GitRemoteInfo?

    /** Initialise a new empty repository at [repoDir]. */
    suspend fun init(repoDir: File)
}
