package com.ahmadkharfan.androidstudiolite.domain.model

/** User-actionable failures produced by Git operations. */
sealed class GitException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Auth(message: String, cause: Throwable? = null) : GitException(message, cause)
    class Network(message: String, cause: Throwable? = null) : GitException(message, cause)
    class NonFastForward(message: String, cause: Throwable? = null) : GitException(message, cause)
    class StaleLease(message: String, cause: Throwable? = null) : GitException(message, cause)
    class MergeConflict(message: String, cause: Throwable? = null) : GitException(message, cause)
    class RepositoryLocked(message: String, cause: Throwable? = null) : GitException(message, cause)
    class PartialStaging(message: String, cause: Throwable? = null) : GitException(message, cause)
    class TooLarge(message: String, cause: Throwable? = null) : GitException(message, cause)
    class BranchNotMerged(message: String, cause: Throwable? = null) : GitException(message, cause)
    class CurrentBranch(message: String, cause: Throwable? = null) : GitException(message, cause)
    class Unknown(message: String, cause: Throwable? = null) : GitException(message, cause)
}
