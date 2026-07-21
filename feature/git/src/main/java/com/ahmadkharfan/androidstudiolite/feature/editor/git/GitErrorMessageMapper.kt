package com.ahmadkharfan.androidstudiolite.feature.git

import com.ahmadkharfan.androidstudiolite.domain.model.GitException

fun gitErrorMessage(error: Throwable): String = when (error) {
    is GitException.Auth -> "Authentication failed. Check your token in settings."
    is GitException.NonFastForward -> "Remote has new commits. Pull first."
    is GitException.StaleLease -> "Force push lease is stale. Fetch first."
    is GitException.MergeConflict -> "Merge produced conflicts. Resolve them in the Conflicts section."
    is GitException.RepositoryLocked -> "Repository is busy. Try again."
    is GitException.PartialStaging -> error.message ?: "Couldn't update the selected hunk"
    is GitException.TooLarge -> error.message ?: "File is too large for this Git view"
    is GitException.BranchNotMerged -> error.message ?: "Branch contains unmerged commits"
    is GitException.CurrentBranch -> error.message ?: "The current branch cannot be deleted"
    is GitException.Network -> error.message ?: "Network operation failed"
    is GitException.Unknown -> error.message ?: "Git operation failed"
    else -> error.message ?: "Git operation failed"
}
