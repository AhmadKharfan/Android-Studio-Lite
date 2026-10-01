package com.ahmadkharfan.androidstudiolite.domain.buildsystem

/**
 * Creates Cloud Build's private build storage (a repository) on the user's GitHub account, when a GitHub
 * sign-in on this device is allowed to. It never makes a public repository, and never changes, replaces or
 * deletes one that already exists.
 */
interface CloudBuildStorageCreator {

    /** True when a GitHub sign-in that might create repositories exists on this device. [create] decides. */
    val isAvailable: Boolean

    suspend fun create(): CloudBuildStorageCreation
}

/** What [CloudBuildStorageCreator.create] did, or why it didn't. */
sealed interface CloudBuildStorageCreation {

    /** A new private build storage was created. */
    data class Created(val storage: CloudBuildStorage) : CloudBuildStorageCreation

    /** A private build storage with that name already existed; it's used as is. */
    data class AlreadyExists(val storage: CloudBuildStorage) : CloudBuildStorageCreation

    /** A build storage with that name exists but is public; it was left untouched. */
    data class ExistsButPublic(val storage: CloudBuildStorage) : CloudBuildStorageCreation

    /** Cloud Build isn't connected to GitHub, so there's no account to create it for. */
    data object BuildNotConnected : CloudBuildStorageCreation

    /** No GitHub sign-in on this device can create repositories. */
    data object NoSignIn : CloudBuildStorageCreation

    /** The sign-in that would create it has expired or was revoked. */
    data object SignInExpired : CloudBuildStorageCreation

    /** The sign-in belongs to [signInAccount], not the [buildAccount] Cloud Build uses; nothing was created. */
    data class AccountMismatch(val signInAccount: String, val buildAccount: String) : CloudBuildStorageCreation

    /** GitHub doesn't let the sign-in create private repositories (e.g. it lacks the `repo` scope). */
    data object NotAllowed : CloudBuildStorageCreation

    data object Offline : CloudBuildStorageCreation

    data object GitHubUnavailable : CloudBuildStorageCreation

    /** GitHub answered in a way that isn't recognised; [message] is GitHub's own text. */
    data class Failed(val message: String) : CloudBuildStorageCreation
}
