package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.CloneProgress
import com.ahmadkharfan.androidstudiolite.domain.model.GitBranch
import com.ahmadkharfan.androidstudiolite.domain.model.GitCommit
import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffLine
import com.ahmadkharfan.androidstudiolite.domain.model.GitState
import com.ahmadkharfan.androidstudiolite.domain.model.GitSyncResult
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
     * Clone [url] into a directory named after the repository under the projects home, emitting
     * progress. The terminal emission carries [CloneProgress.clonedProjectId] = the created folder
     * name (which doubles as the project id). [credentials] are used for private HTTPS remotes and,
     * when non-null, persisted for later fetch/push.
     */
    fun clone(url: String, branch: String?, credentials: GitCredentials?): Flow<CloneProgress>

    /** Hot state of the repository status, updated on [refresh] and after mutating calls. */
    fun observeState(repoDir: File): StateFlow<GitState>

    /** Recompute status for [repoDir] and push it to [observeState] subscribers. */
    suspend fun refresh(repoDir: File)

    /** Unified diff lines for [path] (relative to [repoDir]); HEAD vs working tree. */
    suspend fun getDiff(repoDir: File, path: String): List<GitDiffLine>

    /** Stage [path] (git add), including deletions. */
    suspend fun stage(repoDir: File, path: String)

    /** Unstage [path] (git reset HEAD -- path), keeping working-tree contents. */
    suspend fun unstage(repoDir: File, path: String)

    /** Remember the in-progress commit message so it survives status refreshes. */
    suspend fun setCommitMessage(repoDir: File, message: String)

    /** Commit staged changes (staging all tracked modifications if nothing is staged). Returns the id. */
    suspend fun commit(repoDir: File): String

    suspend fun branches(repoDir: File): List<GitBranch>

    /** Create [name]; when [checkout] is true, switch to it. */
    suspend fun createBranch(repoDir: File, name: String, checkout: Boolean = true)

    suspend fun checkout(repoDir: File, name: String)

    suspend fun log(repoDir: File, max: Int = 50): List<GitCommit>

    /** Push the current branch to its remote, resolving stored credentials for the remote. */
    suspend fun push(repoDir: File): GitSyncResult

    /** Pull (fetch + merge) the current branch from its remote. */
    suspend fun pull(repoDir: File): GitSyncResult

    suspend fun isRepository(repoDir: File): Boolean

    /** Initialise a new empty repository at [repoDir]. */
    suspend fun init(repoDir: File)
}
