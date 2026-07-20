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

interface GitRepository {

    fun clone(
        url: String,
        destination: File,
        options: CloneOptions,
        credentials: GitCredentials?,
    ): Flow<CloneProgress>

    fun observeState(repoDir: File): StateFlow<GitState>

    suspend fun refresh(repoDir: File, includeIgnored: Boolean = false)

    suspend fun onAppForegrounded(repoDir: File) = refresh(repoDir)

    suspend fun diffIndexToWorktree(repoDir: File, path: String, force: Boolean = false): GitFileDiff

    suspend fun diffHeadToIndex(repoDir: File, path: String, force: Boolean = false): GitFileDiff

    suspend fun diffCommitToParent(
        repoDir: File,
        commitId: String,
        path: String,
        force: Boolean = false,
    ): GitFileDiff

    suspend fun diffIndexToBuffer(repoDir: File, path: String, buffer: String): GitFileDiff

    suspend fun stageHunk(repoDir: File, path: String, hunk: GitDiffHunk)

    suspend fun unstageHunk(repoDir: File, path: String, hunk: GitDiffHunk)

    suspend fun stage(repoDir: File, path: String)

    suspend fun unstage(repoDir: File, path: String)

    suspend fun stageAll(repoDir: File)

    suspend fun unstageAll(repoDir: File)

    suspend fun setCommitMessage(repoDir: File, message: String)

    suspend fun commit(repoDir: File, amend: Boolean = false): String

    suspend fun getAuthorConfig(repoDir: File): GitAuthorConfigState

    suspend fun setLocalAuthor(repoDir: File, config: GitAuthorConfig?)

    suspend fun setAppGlobalAuthor(config: GitAuthorConfig)

    suspend fun branches(repoDir: File): List<GitBranch>

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

    suspend fun push(repoDir: File, setUpstreamIfMissing: Boolean = true): GitSyncResult

    suspend fun pushForceWithLease(repoDir: File): GitSyncResult

    suspend fun pull(repoDir: File, mode: PullMode = PullMode.MERGE): GitSyncResult

    suspend fun isRepository(repoDir: File): Boolean

    suspend fun remoteInfo(repoDir: File): GitRemoteInfo?

    suspend fun init(repoDir: File)
}
