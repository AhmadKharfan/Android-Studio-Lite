package com.ahmadkharfan.androidstudiolite.feature.git

import com.ahmadkharfan.androidstudiolite.domain.model.GitException
import org.junit.Assert.assertEquals
import org.junit.Test

class GitErrorMessageMapperTest {
    @Test fun `auth error is actionable`() = assertEquals(
        "Authentication failed. Check your token in settings.",
        gitErrorMessage(GitException.Auth("denied")),
    )

    @Test fun `non fast forward suggests pull`() = assertEquals(
        "Remote has new commits. Pull first.",
        gitErrorMessage(GitException.NonFastForward("rejected")),
    )

    @Test fun `merge conflict points to conflict section`() = assertEquals(
        "Merge produced conflicts. Resolve them in the Conflicts section.",
        gitErrorMessage(GitException.MergeConflict("conflict")),
    )

    @Test fun `repository lock suggests retry`() = assertEquals(
        "Repository is busy. Try again.",
        gitErrorMessage(GitException.RepositoryLocked("locked")),
    )

    @Test fun `stale lease suggests fetch`() = assertEquals(
        "Force push lease is stale. Fetch first.",
        gitErrorMessage(GitException.StaleLease("changed")),
    )
}
