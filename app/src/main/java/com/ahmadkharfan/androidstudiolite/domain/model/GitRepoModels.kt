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

/**
 * HTTPS credentials for a remote. For GitHub-style personal access tokens the token is sent as the
 * password; [username] may be any non-blank value (GitHub ignores it for PAT auth) and defaults to
 * `x-access-token` when left blank.
 */
data class GitCredentials(
    val username: String,
    val token: String,
)

/** Outcome of a push or pull. [detail] carries a human-readable summary or the first rejection reason. */
data class GitSyncResult(
    val success: Boolean,
    val detail: String,
)
