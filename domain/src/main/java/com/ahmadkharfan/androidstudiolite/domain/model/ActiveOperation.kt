package com.ahmadkharfan.androidstudiolite.domain.model

/** A mutating Git operation currently owning a repository's exclusive operation slot. */
data class ActiveOperation(
    val id: Long,
    val type: GitOperationType,
    val label: String,
    val progress: Float? = null,
    val message: String? = null,
    val cancellable: Boolean = false,
)

/** Mutating operations serialized per repository working tree. */
enum class GitOperationType(val defaultLabel: String) {
    STAGE("Staging"),
    UNSTAGE("Unstaging"),
    PARTIAL_STAGE("Updating staged hunk"),
    COMMIT("Committing"),
    FETCH("Fetching"),
    PUSH("Pushing"),
    PULL("Pulling"),
    CHECKOUT("Checking out"),
    CREATE_BRANCH("Creating branch"),
    BRANCH("Updating branch"),
    TAG("Updating tag"),
    STASH("Updating stash"),
    DEEPEN("Deepening history"),
    MERGE("Merging"),
    CHERRY_PICK("Cherry-picking"),
    REVERT("Reverting"),
    REBASE("Rebasing"),
    RESOLVE("Resolving conflict"),
    RESTORE("Restoring files"),
    RESET("Resetting"),
    CLEAN("Cleaning"),
    SUBMODULE("Updating submodules"),
    INIT("Initialising repository"),
    CONFIGURE("Updating Git configuration"),
}
