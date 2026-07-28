package com.ahmadkharfan.androidstudiolite.feature.editor.git

import com.ahmadkharfan.androidstudiolite.domain.model.GitCommitChangeType
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffHunk
import com.ahmadkharfan.androidstudiolite.domain.model.GitRepositoryState
import com.ahmadkharfan.androidstudiolite.domain.model.GitSubmoduleStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class GitDisplayTextTest {
    @Test
    fun `hunk header preserves unified diff syntax`() {
        val hunk = GitDiffHunk(
            oldStart = 2,
            oldCount = 3,
            newStart = 5,
            newCount = 7,
            lines = emptyList(),
        )

        assertEquals("@@ -2,3 +5,7 @@", hunkHeader(hunk))
    }

    @Test
    fun `stash label includes zero index`() {
        assertEquals("stash@{0}", stashLabel(0))
    }

    @Test
    fun `blame gutter truncates long author and preserves fixed widths`() {
        assertEquals(
            "   7  abc1234  abcdefghijkl  ",
            blameGutterText(7, "abc1234", "abcdefghijklmnopqrst"),
        )
    }

    @Test
    fun `behind label uses down arrow`() {
        assertEquals("↓3", behindLabel(3))
    }

    @Test
    fun `ahead label uses up arrow`() {
        assertEquals("↑4", aheadLabel(4))
    }

    @Test
    fun `submodule status replaces underscores with spaces`() {
        assertEquals("checked out", submoduleStatusLabel(GitSubmoduleStatus.CHECKED_OUT))
    }

    @Test
    fun `repository state sentence label remains lowercase`() {
        assertEquals(
            "cherry_picking",
            repositoryStateSentenceLabel(GitRepositoryState.CHERRY_PICKING),
        )
    }

    @Test
    fun `repository state label capitalizes only the first character`() {
        assertEquals(
            "Cherry_picking",
            repositoryStateLabel(GitRepositoryState.CHERRY_PICKING),
        )
    }

    @Test
    fun `change type label uses enum initial`() {
        assertEquals("R", changeTypeLabel(GitCommitChangeType.RENAMED))
    }
}
