package com.ahmadkharfan.androidstudiolite.feature.git

import com.ahmadkharfan.androidstudiolite.feature.git.api.gitErrorMessage
import com.ahmadkharfan.androidstudiolite.domain.model.GitException
import org.junit.Assert.assertEquals
import org.junit.Test

class GitErrorMessageMapperTest {
    @Test
    fun authErrorIsActionable() {
        assertEquals(
            "Authentication failed. Check your token in settings.",
            gitErrorMessage(GitException.Auth("denied")),
        )
    }

    @Test
    fun nonFastForwardSuggestsPull() {
        assertEquals(
            "Remote has new commits. Pull first.",
            gitErrorMessage(GitException.NonFastForward("rejected")),
        )
    }

    @Test
    fun mergeConflictPointsToConflictSection() {
        assertEquals(
            "Merge produced conflicts. Resolve them in the Conflicts section.",
            gitErrorMessage(GitException.MergeConflict("conflict")),
        )
    }

    @Test
    fun repositoryLockSuggestsRetry() {
        assertEquals(
            "Repository is busy. Try again.",
            gitErrorMessage(GitException.RepositoryLocked("locked")),
        )
    }

    @Test
    fun staleLeaseSuggestsFetch() {
        assertEquals(
            "Force push lease is stale. Fetch first.",
            gitErrorMessage(GitException.StaleLease("changed")),
        )
    }
}
